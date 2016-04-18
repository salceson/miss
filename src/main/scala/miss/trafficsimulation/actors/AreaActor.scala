package miss.trafficsimulation.actors

import akka.actor.{ActorRef, FSM, UnhandledMessage}
import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.actors.AreaActor.{Data, State}
import miss.trafficsimulation.roads.{Area, AreaRoadDefinition, RoadId, VehicleAndCoordinates}

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
      goto(Simulating) using AreaData(area, 0)
  }

  when(Simulating) {
    case Event(msg@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic), d@AreaData(area, currentTimeFrame)) =>
      log.info(s"Got $msg")
      area.putIncomingTraffic(roadId, timeFrame, outgoingTraffic)
      if (area.isReadyForComputation(currentTimeFrame)) {
        self ! ReadyForComputation(currentTimeFrame)
      }
      stay
    case Event(ReadyForComputation(timeframe), AreaData(area, previousTimeFrame)) if previousTimeFrame == timeframe =>
      val currentTimeFrame = previousTimeFrame + 1
      val outgoingTraffic = area.simulate()
      outgoingTraffic groupBy {
        case (actorRef, roadId, _) => (actorRef, roadId)
      } foreach {
        case ((actorRef, roadId), list) =>
          actorRef ! OutgoingTrafficInfo(roadId, currentTimeFrame, list map {
            case (_, _, vac) => vac
          })
      }
      if (area.isReadyForComputation(currentTimeFrame)) {
        self ! ReadyForComputation(currentTimeFrame)
      }
      //TODO: Handle this here
      goto(Simulating) using AreaData(area, currentTimeFrame)
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

  case class VisualizationStartRequest()

  case class VisualizationStopRequest()

  // States:

  sealed trait State

  case object Initialized extends State

  case object Simulating extends State

  // Data:

  sealed trait Data

  case object EmptyData extends Data

  case class AreaData(area: Area, timeFrame: Long) extends Data

}
