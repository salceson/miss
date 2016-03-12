package miss.trafficsimulation.actors

import akka.actor.Actor

class AreaActor extends Actor {

  import AreaActor._

  override def receive: Receive = {
    case x: Test =>
  }
}

object AreaActor {

  case class Test()

}
