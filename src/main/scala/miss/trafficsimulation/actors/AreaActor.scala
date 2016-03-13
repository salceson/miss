package miss.trafficsimulation.actors

import akka.actor.{Actor, Props}
import miss.trafficsimulation.roads.{Area, RoadId}
import miss.trafficsimulation.traffic.Vehicle

class AreaActor(area: Area) extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case IncomingTrafficInfo(roadId, turnNumber, carsPerLane) =>
    case OutgoingTrafficInfo(roadId, turnNumber, carsPerLane) =>
    case ReadyForComputation(turnNumber) =>
  }
}

object AreaActor {

  def props(area: Area): Props = Props(classOf[AreaActor], area)

  class TrafficInfo(roadId: RoadId,
                    timeFrameNumber: Long,
                    carsPerLane: List[Option[(Vehicle, Long)]])

  case class IncomingTrafficInfo(roadId: RoadId,
                                 timeFrameNumber: Long,
                                 carsPerLane: List[Option[(Vehicle, Long)]])
    extends TrafficInfo(roadId, timeFrameNumber, carsPerLane)

  case class OutgoingTrafficInfo(roadId: RoadId,
                                 timeFrameNumber: Long,
                                 carsPerLane: List[Option[(Vehicle, Long)]])
    extends TrafficInfo(roadId, timeFrameNumber, carsPerLane)

  case class ReadyForComputation(timeFrameNumber: Long)

}
