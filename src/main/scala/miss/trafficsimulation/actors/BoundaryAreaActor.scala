package miss.trafficsimulation.actors

import akka.actor._

class BoundaryAreaActor(inActor: ActorRef, outActor: ActorRef) extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case msg: OutgoingTrafficInfo =>
      outActor ! msg
    case msg: AvailableRoadspaceInfo =>
      inActor ! msg
  }
}

object BoundaryAreaActor {
  def props(inActor: ActorRef, outActor: ActorRef) = Props(classOf[BoundaryAreaActor], inActor, outActor)
}