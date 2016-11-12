package miss.worker

import akka.actor.{Actor, ActorLogging, Props}
import miss.supervisor.Supervisor.{RegisterWorker, UnregisterWorker}

import scala.concurrent.duration._
import scala.language.postfixOps

class WorkerActor(supervisorPath: String) extends Actor with ActorLogging {

  import WorkerActor._
  import context._

  val supervisor = context.actorSelection(supervisorPath)

  override def receive: Receive = {
    case Start =>
      log.info("Sending RegisterWorker to supervisor")
      supervisor ! RegisterWorker
    case Terminate =>
      log.info("Sending UnregisterWorker to supervisor")
      supervisor ! UnregisterWorker
      context.system.scheduler.scheduleOnce(5 seconds, self, TerminateSystem)
    case TerminateSystem =>
      log.info("Terminating system")
      context.system.terminate()
  }

}

object WorkerActor {

  // Messages:
  case object Start

  case object Terminate

  case object TerminateSystem

  def props(supervisorPath: String): Props = Props(classOf[WorkerActor], supervisorPath)

}