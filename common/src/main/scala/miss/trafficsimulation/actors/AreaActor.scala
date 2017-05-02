package miss.trafficsimulation.actors

import akka.actor.{ActorPath, ActorRef, FSM, Props, Stash}
import akka.pattern.after
import com.typesafe.config.Config
import miss.common.SerializableMessage
import miss.supervisor.Supervisor
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads.RoadDirection._
import miss.trafficsimulation.roads._
import miss.visualization.VisualizationActor.TrafficState

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class AreaActor(config: Config) extends FSM[State, Data] with Stash {

  import AreaActor._
  import Supervisor.SimulationResult

  private val visualizationFrameDelay = config.getInt("trafficsimulation.visualization.delay")
  private val resolveActorDelaySeconds = config.getInt("trafficsimulation.resolve_neighbours.delay.seconds")
  private val resolveActorRetries = config.getInt("trafficsimulation.resolve_neighbours.retries")
  private val resolveActorTimeoutSeconds = config.getInt("trafficsimulation.resolve_neighbours.timeout.seconds")

  startWith(Initialized, EmptyData)

  private def retry[T](f: => Future[T], delay: FiniteDuration, retries: Int)(implicit ec: ExecutionContext): Future[T] = {
    f recoverWith { case _ if retries > 0 => after(delay, context.system.scheduler)(retry(f, delay, retries - 1)) }
  }

  private def resolveActor(actorPath: ActorPath): Unit = {
    log.debug(s"Resolving $actorPath")

    def resolveActorWithRetries(actorPath: ActorPath, retries: Int)(implicit ec: ExecutionContext): Future[ActorRef] = {
      val future = this.context.actorSelection(actorPath).resolveOne(resolveActorTimeoutSeconds seconds)
      future onComplete {
        case Failure(e) if retries > 0 =>
          log.info(s"Cannot resolve actor: $actorPath due to: ${e.getMessage}. Retrying.")
        case Failure(e) =>
          log.error(s"Cannot resolve actor: $actorPath due to: ${e.getMessage}. Shutting down.")
          context.system.terminate()
        case Success(actorRef) =>
          log.debug(s"Resolved actor: ${actorRef.path}")
          self ! ResolvedActor(actorPath, actorRef)
      }
      future recoverWith {
        case _ if retries > 0 => after(resolveActorDelaySeconds seconds, context.system.scheduler)(resolveActorWithRetries(actorPath, retries - 1))
      }
    }

    resolveActorWithRetries(actorPath, resolveActorRetries)
  }

  when(Initialized) {
    case Event(msg@StartSimulation(verticalRoadsDefs, horizontalRoadsDefs, x, y), EmptyData) =>
      val actorsToResolve = verticalRoadsDefs.flatMap(vrd => Set(vrd.prevAreaActorPath, vrd.outgoingActorPath)).toSet ++ horizontalRoadsDefs.flatMap(hrd => Set(hrd.prevAreaActorPath, hrd.outgoingActorPath)).toSet
      //      actorsToResolve.foreach(actorPath => resolveActor(actorPath))
      resolveActor(actorsToResolve.head)
      val supervisor = sender()
      goto(ResolvingActors) using AreaActor.ResolvingActorsData(msg, Map(), actorsToResolve, supervisor)
    case Event(_, _) =>
      stash
      stay
  }

  when(ResolvingActors) {
    case Event(msg@ResolvedActor(actorPath, actorRef), rad@ResolvingActorsData(_, actorRefs, actorsToResolve, _)) if actorsToResolve.size > 1 =>
      val updatedActorsToResolve = actorsToResolve - actorPath
      resolveActor(updatedActorsToResolve.head)
      stay using rad.copy(actorRefs = actorRefs + (actorPath -> actorRef), actorsToResolve = updatedActorsToResolve)
    case Event(msg@ResolvedActor(actorPath, actorRef), rad@ResolvingActorsData(startSimulationMessage, actorRefs, actorsToResolve, supervisor)) if actorsToResolve.size == 1 && actorsToResolve.contains(actorPath) =>
      log.info(s"Actor (${startSimulationMessage.x}, ${startSimulationMessage.y}) starting simulation...")

      val actorRefsMap = actorRefs + (actorPath -> actorRef)

      def getActorRef(actorPath: ActorPath) = {
        val actorRefOption = actorRefsMap.get(actorPath)
        if (actorRefOption.isEmpty) {
          log.error(s"Cannot get actorRef for path: $actorPath. Shutting down.")
          System.exit(1)
        }
        actorRefOption.get
      }

      val verticalRoadsData = startSimulationMessage.verticalRoadsDefs.map(r => AreaRoadData(r.roadId, r.direction, getActorRef(r.outgoingActorPath), getActorRef(r.prevAreaActorPath)))
      val horizontalRoadsData = startSimulationMessage.horizontalRoadsDefs.map(r => AreaRoadData(r.roadId, r.direction, getActorRef(r.outgoingActorPath), getActorRef(r.prevAreaActorPath)))

      val area = new Area(verticalRoadsData, horizontalRoadsData, config)
      // sending initial available road space info
      sendAvailableRoadSpaceInfo(area)
      self ! ReadyForComputation(0)
      unstashAll
      goto(Simulating) using AreaData(area, None, startSimulationMessage.x, startSimulationMessage.y, supervisor, 0)
    case Event(msg@ResolvedActor(_, _), data@ResolvingActorsData(_, _, _, _)) =>
      log.warning(s"Got unexpected message: $msg in state ResolvingActors with data: $data")
      stay
    case Event(_, _) =>
      stash
      stay
  }

  when(Simulating) {
    case Event(msg@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic), d: AreaData) =>
      val area = d.area
      log.debug(s"Got $msg")
      val updatedRoads = area.putIncomingTraffic(msg)
      sendAvailableRoadSpaceInfo(area, updatedRoads)
      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      stay
    case Event(msg@AvailableRoadspaceInfo(roadId, timeFrame, availableSpacePerLane), d: AreaData) =>
      val area = d.area
      log.debug(s"Got $msg")
      area.updateNeighboursAvailableRoadspace(msg)
      stay
    case Event(msg@ReadyForComputation(timeFrame), data@AreaData(area, visualizer, x, y, supervisor, firstSimulationFrame)) if area.currentTimeFrame == timeFrame =>
      log.debug(s"Time frame: $timeFrame")
      log.debug(s"Got $msg")
      log.debug(s"Simulating timeFrame ${area.currentTimeFrame + 1}...")
      // log.debug(area.printVehiclesPos())
      val beforeCarsCount = area.countCars()
      log.debug(s"Total cars before simulation: " + beforeCarsCount)
      val outgoingTraffic = area.simulate()
      val afterCarsCount = area.countCars()
      log.debug(s"Total cars after simulation: " + afterCarsCount)
      log.debug(s"Sent cars: " + outgoingTraffic.size)

      log.debug(s"Done simulation of timeFrame ${area.currentTimeFrame}")
      // log.debug(area.printVehiclesPos())
      log.debug(s"Messages to send: $outgoingTraffic")

      if (afterCarsCount + outgoingTraffic.size != beforeCarsCount) {
        log.error("Some cars are missing: " + (beforeCarsCount - outgoingTraffic.size - afterCarsCount))
        throw new RuntimeException("Some cars are missing: " + (beforeCarsCount - outgoingTraffic.size - afterCarsCount))
      }

      // sending outgoing traffic
      val messagesSent = mutable.Map(area.outgoingActorsAndRoadIds.map({
        case (a: ActorRef, r: RoadId) => (a, r) -> false
      }): _*)
      outgoingTraffic groupBy {
        case (actor, roadId, _) => (actor, roadId)
      } foreach {
        case ((actor, roadId), list) =>
          log.debug(s"Sending to $actor; roadId: $roadId; traffic: $list")
          messagesSent((actor, roadId)) = true
          actor ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, list map {
            case (_, _, vac) => vac
          })
      }
      messagesSent foreach {
        case ((actor, roadId), false) =>
          log.debug(s"Sending to $actor; roadId: $roadId; traffic: No traffic")
          actor ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, List())
        case _ =>
      }

      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      if (visualizer.isDefined) {
        Thread.sleep(visualizationFrameDelay)
        visualizer.get ! TrafficState(area.horizontalRoads.view.toList,
          area.verticalRoads.view.toList,
          area.intersectionGreenLightsDirection,
          area.currentTimeFrame)
      }
      stay using data
    case Event(ReadyForComputation(timeFrame), AreaData(area, _, _, _, _, _)) if area.currentTimeFrame != timeFrame =>
      stay
    case Event(EndWarmUpPhase, ad: AreaData) =>
      stay using ad.copy(firstSimulationFrame = ad.area.currentTimeFrame)
    case Event(EndSimulation, AreaData(area, visualizer, x, y, supervisor, firstSimulationFrame)) =>
      val computedFrames = area.currentTimeFrame - firstSimulationFrame
      supervisor ! SimulationResult(x, y, computedFrames)
      log.info(s"Simulation result: computed frames: $computedFrames, firstFrame: $firstSimulationFrame")
      stop
    case Event(VisualizationStartRequest(visualizer), ad: AreaData) =>
      stay using ad.copy(visualizer = Some(visualizer))
    case Event(VisualizationStopRequest(_), ad: AreaData) =>
      stay using ad.copy(visualizer = None)
    // The code below is to avoid the unhandled message warning in the console. The
    // warning is showing because sometimes we send the message to the actor too
    // many times but simulation is handled only if the time frames are the same.
    case Event(_, _) => stay
  }

  def sendAvailableRoadSpaceInfo(area: Area) = {
    val availableRoadSpaceInfoList = area.getAvailableSpaceInfo
    availableRoadSpaceInfoList foreach {
      case ((actor, roadId, list)) =>
        log.debug(s"Sending to $actor; roadId: $roadId; available space: $list")
        actor ! AvailableRoadspaceInfo(roadId, area.currentTimeFrame, list)
      case _ =>
    }
  }

  def sendAvailableRoadSpaceInfo(area: Area, updatedRoads: Map[RoadId, Long] = Map()) = {
    val availableRoadSpaceInfoList = area.getAvailableSpaceInfo
    availableRoadSpaceInfoList foreach {
      case ((actor, roadId, list)) if updatedRoads.contains(roadId) =>
        log.debug(s"Sending to $actor; roadId: $roadId; timeframe: ${updatedRoads(roadId)} available space: $list")
        actor ! AvailableRoadspaceInfo(roadId, updatedRoads(roadId), list)
      case _ =>
    }
  }

  log.debug(s"Actor ${self.path} initialized")
}

object AreaActor {

  def props(config: Config): Props = Props(classOf[AreaActor], config)

  case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, outgoingActorPath: ActorPath, prevAreaActorPath: ActorPath) extends SerializableMessage

  // Messages:

  case class StartSimulation(verticalRoadsDefs: List[AreaRoadDefinition],
                             horizontalRoadsDefs: List[AreaRoadDefinition],
                             x: Int,
                             y: Int)
    extends SerializableMessage

  case object EndWarmUpPhase extends SerializableMessage

  case object EndSimulation extends SerializableMessage

  case class AvailableRoadspaceInfo(roadId: RoadId,
                                    timeframe: Long,
                                    availableSpacePerLane: List[Int])
    extends SerializableMessage

  case class OutgoingTrafficInfo(roadId: RoadId,
                                 timeframe: Long,
                                 outgoingTraffic: List[VehicleAndCoordinates])
    extends SerializableMessage

  case class ReadyForComputation(timeframe: Long)

  case class VisualizationStartRequest(visualizer: ActorRef)

  case class VisualizationStopRequest(visualizer: ActorRef)

  case class ResolvedActor(actorPath: ActorPath, actorRef: ActorRef)

  // States:

  sealed trait State

  case object Initialized extends State

  case object ResolvingActors extends State

  case object Simulating extends State

  // Data:

  sealed trait Data

  case object EmptyData extends Data

  case class ResolvingActorsData(startSimulationMessage: StartSimulation,
                                 actorRefs: Map[ActorPath, ActorRef],
                                 actorsToResolve: Set[ActorPath],
                                 supervisor: ActorRef
                                ) extends Data

  case class AreaData(area: Area,
                      visualizer: Option[ActorRef],
                      x: Int,
                      y: Int,
                      supervisor: ActorRef,
                      firstSimulationFrame: Long)
    extends Data

}
