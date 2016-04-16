package miss.trafficsimulation.actors

import akka.actor.{Actor, Props}
import miss.trafficsimulation.roads.{Area, RoadId, VehicleAndCoordinates}

class AreaActor(area: Area) extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case AvailableRoadspaceInfo(roadId, timeframe, availableSpacePerLane) =>
    case OutgoingTrafficInfo(roadId, timeframe, outgoingTraffic) =>
    case ReadyForComputation(turnNumber) =>
  }
}

object AreaActor {

  def props(area: Area): Props = Props(classOf[AreaActor], area)

  case class AvailableRoadspaceInfo(roadId: RoadId,
                                    timeframe: Long,
                                    availableSpacePerLane: List[Int])

  case class OutgoingTrafficInfo(roadId: RoadId,
                                 timeframe: Long,
                                 outgoingTraffic: List[VehicleAndCoordinates])

  case class ReadyForComputation(timeframe: Long)

  case class VisualizationStartRequest()

  case class VisualizationStopRequest()

}
