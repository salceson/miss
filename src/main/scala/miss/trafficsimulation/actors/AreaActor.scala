package miss.trafficsimulation.actors

import akka.actor.{ActorRef, FSM}
import miss.supervisor.Supervisor
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads.{Area, AreaRoadDefinition, RoadId, VehicleAndCoordinates}
import miss.visualization.VisualizationActor.TrafficState

import scala.collection.mutable

class AreaActor extends FSM[State, Data] {

  import AreaActor._
  import Supervisor._

  val config = context.system.settings.config
  val timeout = config.getInt("trafficsimulation.visualization.delay")
  val initialTimeout = config.getInt("trafficsimulation.area.initial_delay")

  startWith(Initialized, EmptyData)

  when(Initialized) {
    case Event(StartSimulation(verticalRoadsDefs, horizontalRoadsDefs, x, y), EmptyData) =>
      val supervisor = sender()
      log.info(s"Actor ($x, $y) starting simulation...")
      val area = new Area(verticalRoadsDefs, horizontalRoadsDefs, config)
      Thread.sleep(initialTimeout) // This is to avoid sending messages to uninitialized actors
      self ! ReadyForComputation(0)
      goto(Simulating) using AreaData(area, None, x, y, supervisor)
  }

  when(Simulating) {
    case Event(msg@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic), d: AreaData) =>
      val area = d.area
      log.debug(s"Got $msg")
      area.putIncomingTraffic(msg)
      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      stay
    case Event(msg@ReadyForComputation(timeFrame), data@AreaData(area, visualizer, x, y, supervisor)) if area.currentTimeFrame == timeFrame =>
      log.info(s"Time frame: $timeFrame")
      log.debug(s"Got $msg")
      log.info(s"Simulating timeFrame ${area.currentTimeFrame + 1}...")
      // log.debug(area.printVehiclesPos())
      supervisor ! TimeFrameUpdate(x, y, area.currentTimeFrame + 1)
      val beforeCarsCount = area.countCars()
      log.debug(s"Total cars before simulation: " + beforeCarsCount)
      val outgoingTraffic = area.simulate()
      val afterCarsCount = area.countCars()
      log.debug(s"Total cars after simulation: " + afterCarsCount)
      log.debug(s"Sent cars: " + outgoingTraffic.size)

      log.info(s"Done simulation of timeFrame ${area.currentTimeFrame}")
      // log.debug(area.printVehiclesPos())
      log.debug(s"Messages to send: $outgoingTraffic")

      if (afterCarsCount + outgoingTraffic.size != beforeCarsCount) {
        log.error("Some cars are missing: " + (beforeCarsCount - outgoingTraffic.size - afterCarsCount))
        throw new RuntimeException("Some cars are missing: " + (beforeCarsCount - outgoingTraffic.size - afterCarsCount))
      }

      val messagesSent = mutable.Map(area.actorsAndRoadIds.map({
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
      goto(Simulating) using data
    case Event(VisualizationStartRequest(visualizer), ad: AreaData) =>
      goto(Simulating) using ad.copy(visualizer = Some(visualizer))
    case Event(VisualizationStopRequest(_), ad: AreaData) =>
      goto(Simulating) using ad.copy(visualizer = None)
    // The code below is to avoid the unhandled message warning in the console. The
    // warning is showing because sometimes we send the message to the actor too
    // many times but simulation is handled only if the time frames are the same.
    case Event(_, _) => stay
  }
}

object AreaActor {

  // Messages:

  case class StartSimulation(verticalRoadsDefs: List[AreaRoadDefinition],
                             horizontalRoadsDefs: List[AreaRoadDefinition],
                             x: Int,
                             y: Int)

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
                      supervisor: ActorRef)
    extends Data

}
