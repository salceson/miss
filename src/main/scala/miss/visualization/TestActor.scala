package miss.visualization

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, Props}

object TestActor {
  def props(visualizationActor: ActorRef): Props = Props(classOf[TestActor], visualizationActor)

  case object Init
}

class TestActor(visualizationActor: ActorRef) extends Actor {

  import TestActor._

  override def receive: Receive = {
    case Init =>
      
  }
}
