package miss.trafficsimulation.actors

import akka.actor._

class BoundaryAreaActor(inActor: ActorRef, outActor: ActorRef) extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case msg: OutgoingTrafficInfo =>
      val senderActor = sender()
      senderActor match {
        case a: ActorRef if a == inActor => outActor ! msg
        case a: ActorRef if a == outActor => inActor ! msg
      }
    case msg: AvailableRoadspaceInfo =>
      val senderActor = sender()
      senderActor match {
        case a: ActorRef if a == inActor => outActor ! msg
        case a: ActorRef if a == outActor => inActor ! msg
      }
  }
}

object BoundaryAreaActor {
  def props(inActor: ActorRef, outActor: ActorRef) = Props(classOf[BoundaryAreaActor], inActor, outActor)
}