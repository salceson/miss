package miss.cityvisualization

import java.util.Date

import akka.actor._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.language.postfixOps

class SpeedCounter(system: ActorSystem) {

  import SpeedCounterActor._
  import system.dispatcher

  private var messagesTimestamps = ListBuffer[Date]()
  private val actor = system.actorOf(SpeedCounterActor.props(this))
  system.scheduler.schedule(10 seconds, 1 second, actor, Clear)

  def message(): Unit = {
    synchronized {
      messagesTimestamps += new Date()
    }
  }

  def calculateSpeed(): Double = {
    synchronized {
      messagesTimestamps.length / 10.0
    }
  }

  def clear(): Unit = {
    synchronized {
      val currentTimeStamp = new Date()
      messagesTimestamps = messagesTimestamps.filter(
        date => (currentTimeStamp.getTime - date.getTime) <= 10 * 1000
      )
    }
  }

}

class SpeedCounterActor(speedCounter: SpeedCounter) extends Actor {

  import SpeedCounterActor._

  override def receive: Receive = {
    case Clear => speedCounter.clear()
  }
}

object SpeedCounterActor {

  def props(speedCounter: SpeedCounter) = Props(classOf[SpeedCounterActor], speedCounter)

  case object Clear

}
