package miss.trafficsimulation.actors

import akka.actor.{ActorRef, FSM}
import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads.{Area, AreaRoadDefinition, RoadId, VehicleAndCoordinates}
import miss.visualization.VisualizationActor.TrafficState

import scala.collection.mutable

class AreaActor extends FSM[State, Data] {

  import AreaActor._

  startWith(Initialized, EmptyData)

  when(Initialized) {
    case Event(StartSimulation(verticalRoadsDefs, horizontalRoadsDefs), EmptyData) =>
      log.info(s"Actor ${this.toString} starting simulation...")
      val config = ConfigFactory.load()
      val area = new Area(verticalRoadsDefs, horizontalRoadsDefs, config)
      Thread.sleep(5000) //TODO: Dirty magic
      self ! ReadyForComputation(0)
      goto(Simulating) using AreaData(area, None)
  }

  when(Simulating) {
    case Event(msg@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic), d@AreaData(area, _)) =>
      log.info(s"Got $msg")
      area.putIncomingTraffic(msg)
      if (area.isReadyForComputation()) {
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      stay
    case Event(msg@ReadyForComputation(timeFrame), data@AreaData(area, visualizer)) if area.currentTimeFrame == timeFrame =>
      log.info(s"Time frame: $timeFrame")
      log.info(s"Got $msg")
      log.info(s"Simulating timeFrame ${area.currentTimeFrame}...")
      val outgoingTraffic = area.simulate()
      log.info(s"Messages to send: $outgoingTraffic")
      val messagesSent = mutable.Map(area.actorsAndRoadIds.map({
        case (a: ActorRef, r: RoadId) => (a, r) -> false
      }): _*)
      outgoingTraffic groupBy {
        case (actorRef, roadId, _) => (actorRef, roadId)
      } foreach {
        case ((actorRef, roadId), list) =>
          log.info(s"Sending to $actorRef; roadId: $roadId; traffic: $list")
          messagesSent((actorRef, roadId)) = true
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, list map {
            case (_, _, vac) => vac
          })
      }
      messagesSent foreach {
        case ((actorRef, roadId), false) =>
          log.info(s"Sending to $actorRef; roadId: $roadId; traffic: No traffic")
          actorRef ! OutgoingTrafficInfo(roadId, area.currentTimeFrame, List())
        case _ =>
      }
      log.info("WTF")
      if (area.isReadyForComputation()) {
        log.info("READY")
        self ! ReadyForComputation(area.currentTimeFrame)
      }
      log.info("NOPE")
      if (visualizer.isDefined) {
        visualizer.get ! TrafficState(area.horizontalRoads.view.toList,
          area.verticalRoads.view.toList,
          area.intersectionGreenLightsDirection)
        Thread.sleep(1000)
      }
      goto(Simulating) using data
    case Event(VisualizationStartRequest(visualizer), AreaData(area, _)) =>
      goto(Simulating) using AreaData(area, Some(visualizer))
    case Event(VisualizationStopRequest(_), AreaData(area, _)) =>
      goto(Simulating) using AreaData(area, None)
  }
}

object AreaActor {

  // Messages:

  case class StartSimulation(verticalRoadsDefs: List[AreaRoadDefinition],
                             horizontalRoadsDefs: List[AreaRoadDefinition])

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

  /**
    * @param area
    */
  case class AreaData(area: Area, visualizer: Option[ActorRef]) extends Data

}
