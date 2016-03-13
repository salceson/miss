package miss.trafficsimulation.actors

import akka.actor.{Actor, Props}
import miss.trafficsimulation.roads.{Area, RoadId}
import miss.trafficsimulation.traffic.Vehicle

class AreaActor(area: Area) extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case TrafficInfo(roadId, turnNumber, carsPerLane) =>
    case ReadyForComputation(turnNumber) =>
  }
}

object AreaActor {

  def props(area: Area): Props = Props(classOf[AreaActor], area)

  case class TrafficInfo(roadId: RoadId, turnNumber: Long, carsPerLane: List[Option[(Vehicle, Long)]])

  case class ReadyForComputation(turnNumber: Long)

}
