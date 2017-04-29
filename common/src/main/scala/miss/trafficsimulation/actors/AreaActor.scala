package miss.trafficsimulation.actors

import akka.actor.{ActorRef, FSM, Props, Stash}
import com.typesafe.config.Config
import miss.common.SerializableMessage
import miss.supervisor.Supervisor
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads._
import miss.trafficsimulation.util.FiniteQueue
import miss.visualization.VisualizationActor.TrafficState

import scala.collection.immutable.Queue
import scala.collection.mutable

class AreaActor(config: Config) extends FSM[State, Data] with Stash {

  import AreaActor._
  import Supervisor.SimulationResult

  val timeout = config.getInt("trafficsimulation.visualization.delay")

  private var lastIncomingMessagesQueue = Queue[String]()
  private var lastSentMessagesQueue = Queue[String]()
  private val debugQueueSize = 150

  implicit def queue2finitequeue[A](q: Queue[A]) = new FiniteQueue[A](q)

  startWith(Initialized, EmptyData)

  when(Initialized) {
    case Event(StartSimulation(verticalRoadsDefs, horizontalRoadsDefs, x, y), EmptyData) =>
      val supervisor = sender()
      log.info(s"Actor ($x, $y) starting simulation...")
      val area = new Area(verticalRoadsDefs, horizontalRoadsDefs, config)
      // sending initial available road space info
      sendAvailableRoadSpaceInfo(area)
      self ! ReadyForComputation(0)
      unstashAll
      goto(Simulating) using AreaData(area, None, x, y, supervisor, 0)
    case Event(_, _) =>
      stash
      stay
  }

  def printDebug(info: String, area: Area) = {
    val stringBuilder = new StringBuilder(info)

    stringBuilder.append("\nPriority queue:\n")
    while (area.incomingTrafficQueue.nonEmpty) {
      val x@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic) = area.incomingTrafficQueue.dequeue()
      stringBuilder.append(s"tf=${x.timeframe}, road=${x.roadId}\n")
    }

    stringBuilder.append("\nIncoming messages:\n")
    lastIncomingMessagesQueue.foreach(x => stringBuilder.append(x.toString).append("\n"))

    stringBuilder.append("\nSent messages:\n")
    lastSentMessagesQueue.foreach(x => stringBuilder.append(x.toString).append("\n"))

    log.info(stringBuilder.toString())
  }

  when(Simulating) {
    case Event(msg@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic), d: AreaData) =>
      lastIncomingMessagesQueue = lastIncomingMessagesQueue.enqueueFinite(s"OutgoingTrafficInfo ${sender.path.address} ${sender.path.name} tf=$timeFrame road=$roadId", debugQueueSize)
      val area = d.area
      log.debug(s"Got $msg")
      val updatedRoads = area.putIncomingTraffic(msg)
      sendAvailableRoadSpaceInfo(area, updatedRoads)
      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      } else {
        for (road <- area.horizontalRoads ++ area.verticalRoads) {
          road.elems.head match {
            case firstRoadSeg: RoadSegment =>
              if ((area.currentTimeFrame + 1) - firstRoadSeg.lastIncomingTrafficTimeFrame > area.maxTimeFrameDelay) {
                log.info(s"Not ready for computation tf ${area.currentTimeFrame + 1}: last incoming tf on road ${firstRoadSeg.roadId} (${road.prevAreaActorRef.path.name}) is  ${firstRoadSeg.lastIncomingTrafficTimeFrame}")
              }
            case _ => throw new ClassCastException
          }
        }
      }
      stay
    case Event(msg@AvailableRoadspaceInfo(roadId, timeFrame, availableSpacePerLane), d: AreaData) =>
      lastIncomingMessagesQueue = lastIncomingMessagesQueue.enqueueFinite(s"AvailableRoadspaceInfo \t${sender.path.address} \t${sender.path.name} \ttf=$timeFrame \troad=$roadId", debugQueueSize)
      val area = d.area
      log.debug(s"Got $msg")
      area.updateNeighboursAvailableRoadspace(msg)
      stay
    case Event(msg@ReadyForComputation(timeFrame), data@AreaData(area, visualizer, x, y, supervisor, firstSimulationFrame)) if area.currentTimeFrame == timeFrame =>
      lastIncomingMessagesQueue = lastIncomingMessagesQueue.enqueueFinite(s"ReadyForComputation \t${sender.path.address} \t${sender.path.name} \ttf=$timeFrame", debugQueueSize)
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
        case (actorRef, roadId, _) => (actorRef, roadId)
      } foreach {
        case ((actorRef, roadId), list) =>
          log.debug(s"Sending to $actorRef; roadId: $roadId; traffic: $list")
          messagesSent((actorRef, roadId)) = true
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, list map {
            case (_, _, vac) => vac
          })
          lastSentMessagesQueue = lastSentMessagesQueue.enqueueFinite(s"OutgoingTrafficInfo-traffic \t${actorRef.path.address} \t${actorRef.path.name} \tcTF=${area.currentTimeFrame} \troad=$roadId", debugQueueSize)
      }
      messagesSent foreach {
        case ((actorRef, roadId), false) =>
          log.debug(s"Sending to $actorRef; roadId: $roadId; traffic: No traffic")
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, List())
          lastSentMessagesQueue = lastSentMessagesQueue.enqueueFinite(s"OutgoingTrafficInfo-noTraffic \t${actorRef.path.address} \t${actorRef.path.name} \tcTF=${area.currentTimeFrame} \troad=$roadId", debugQueueSize)
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
      lastIncomingMessagesQueue = lastIncomingMessagesQueue.enqueueFinite(s"ReadyForComputation \t${sender.path.address} \t${sender.path.name} \ttf=$timeFrame", debugQueueSize)
      stay
    case Event(EndWarmUpPhase, ad: AreaData) =>
      stay using ad.copy(firstSimulationFrame = ad.area.currentTimeFrame)
    case Event(EndSimulation, AreaData(area, visualizer, x, y, supervisor, firstSimulationFrame)) =>
      val computedFrames = area.currentTimeFrame - firstSimulationFrame
      supervisor ! SimulationResult(x, y, area.currentTimeFrame)
//      log.info(s"Simulation result: computed frames: $computedFrames, firstFrame: $firstSimulationFrame")
      printDebug(s"Simulation result: computed frames: $computedFrames, firstFrame: $firstSimulationFrame, lastFrame: ${area.currentTimeFrame}", area)
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
        lastSentMessagesQueue = lastSentMessagesQueue.enqueueFinite(s"AvailableRoadspaceInfo \t${actorRef.path.address} \t${actorRef.path.name} \tcTF=${area.currentTimeFrame} \troad=$roadId", debugQueueSize)
        actorRef ! AvailableRoadspaceInfo(roadId, area.currentTimeFrame, list)
      case _ =>
    }
  }

  def sendAvailableRoadSpaceInfo(area: Area, updatedRoads: Map[RoadId, Long] = Map()) = {
    val availableRoadSpaceInfoList = area.getAvailableSpaceInfo
    availableRoadSpaceInfoList foreach {
      case ((actorRef, roadId, list)) if updatedRoads.contains(roadId) =>
        log.debug(s"Sending to $actorRef; roadId: $roadId; timeframe: ${updatedRoads(roadId)} available space: $list")
        lastSentMessagesQueue = lastSentMessagesQueue.enqueueFinite(s"AvailableRoadspaceInfo-UpdatedRoads \t${actorRef.path.address} \t${actorRef.path.name} \ttf=${updatedRoads(roadId)} \troad=$roadId", debugQueueSize)
        actorRef ! AvailableRoadspaceInfo(roadId, updatedRoads(roadId), list)
      case _ =>
    }
  }
}

object AreaActor {

  def props(config: Config): Props = Props(classOf[AreaActor], config)

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

