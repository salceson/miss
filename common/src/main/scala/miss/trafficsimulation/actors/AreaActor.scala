package miss.trafficsimulation.actors

import akka.actor.{ActorPath, ActorRef, FSM, Props}
import akka.contrib.pattern.ReliableProxy
import com.typesafe.config.Config
import miss.supervisor.Supervisor
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads.RoadDirection._
import miss.trafficsimulation.roads._
import miss.visualization.VisualizationActor.TrafficState

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

class AreaActor(config: Config) extends FSM[State, Data] {

  import AreaActor._
  import Supervisor.SimulationResult

  val timeout = config.getInt("trafficsimulation.visualization.delay")
  val initialTimeout = config.getInt("trafficsimulation.area.initial_delay")

  startWith(Initialized, EmptyData)

  private def resolveActor(actorPath: ActorPath): ActorRef = context.actorOf(ReliableProxy.props(actorPath, 100 millis, 1 second))

  when(Initialized) {
    case Event(StartSimulation(verticalRoadsDefs, horizontalRoadsDefs, x, y), EmptyData) =>
      val supervisor = sender()
      log.info(s"Actor ($x, $y) starting simulation...")
      val verticalRoadsData = verticalRoadsDefs.map(r => AreaRoadData(r.roadId, r.direction, resolveActor(r.outgoingActorPath), resolveActor(r.prevAreaActorPath)))
      val horizontalRoadsData = horizontalRoadsDefs.map(r => AreaRoadData(r.roadId, r.direction, resolveActor(r.outgoingActorPath), resolveActor(r.prevAreaActorPath)))

      val area = new Area(verticalRoadsData, horizontalRoadsData, config)
      Thread.sleep(initialTimeout) // This is to avoid sending messages to uninitialized actors
      // sending initial available road space info
      sendAvailableRoadSpaceInfo(area)
      self ! ReadyForComputation(0)
      goto(Simulating) using AreaData(area, None, x, y, supervisor, 0)
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
      log.info(s"Simulating timeFrame ${area.currentTimeFrame + 1}...")
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
        case (actorRef, roadId, _) => (actorRef, roadId)
      } foreach {
        case ((actorRef, roadId), list) =>
          log.debug(s"Sending to $actorRef; roadId: $roadId; traffic: $list")
          messagesSent((actorRef, roadId)) = true
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, list map {
            case (_, _, vac) => vac
          })
      }
      messagesSent foreach {
        case ((actorRef, roadId), false) =>
          log.debug(s"Sending to $actorRef; roadId: $roadId; traffic: No traffic")
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, List())
        case _ =>
      }

      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      if (visualizer.isDefined) {
        Thread.sleep(timeout)
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
      case ((actorRef, roadId, list)) =>
        log.debug(s"Sending to $actorRef; roadId: $roadId; available space: $list")
        actorRef ! AvailableRoadspaceInfo(roadId, area.currentTimeFrame, list)
      case _ =>
    }
  }

  def sendAvailableRoadSpaceInfo(area: Area, updatedRoads: Map[RoadId, Long] = Map()) = {
    val availableRoadSpaceInfoList = area.getAvailableSpaceInfo
    availableRoadSpaceInfoList foreach {
      case ((actorRef, roadId, list)) if updatedRoads.contains(roadId) =>
        log.debug(s"Sending to $actorRef; roadId: $roadId; timeframe: ${updatedRoads(roadId)} available space: $list")
        actorRef ! AvailableRoadspaceInfo(roadId, updatedRoads(roadId), list)
      case _ =>
    }
  }
}

object AreaActor {

  def props(config: Config): Props = Props(classOf[AreaActor], config)

  case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, outgoingActorPath: ActorPath, prevAreaActorPath: ActorPath)

  // Messages:

  case class StartSimulation(verticalRoadsDefs: List[AreaRoadDefinition],
                             horizontalRoadsDefs: List[AreaRoadDefinition],
                             x: Int,
                             y: Int)

  case object EndWarmUpPhase

  case object EndSimulation

  case class AvailableRoadspaceInfo(roadId: RoadId,
                                    timeframe: Long,
                                    availableSpacePerLane: List[Int])

  case class OutgoingTrafficInfo(roadId: RoadId,
                                 timeframe: Long,
                                 outgoingTraffic: List[VehicleAndCoordinates])

  case class ReadyForComputation(timeframe: Long)

  case class VisualizationStartRequest(visualizer: ActorRef)

  case class VisualizationStopRequest(visualizer: ActorRef)

  // States:

  sealed trait State

  case object Initialized extends State

  case object Simulating extends State

  // Data:

  sealed trait Data

  case object EmptyData extends Data

  case class AreaData(area: Area,
                      visualizer: Option[ActorRef],
                      x: Int,
                      y: Int,
                      supervisor: ActorRef,
                      firstSimulationFrame: Long)
    extends Data

}
