package miss.worker

import akka.actor.{Actor, ActorLogging, Props}
import miss.supervisor.Supervisor.RegisterWorker

class WorkerActor(supervisorPath: String) extends Actor with ActorLogging {

  import WorkerActor._

  val supervisor = context.actorSelection(supervisorPath)

  override def receive: Receive = {
    case Start =>
      log.info("Sending RegisterWorker to supervisor")
      supervisor ! RegisterWorker
    case RegisteredAck =>
      log.info("Registered at supervisor.")
    case Terminate =>
      log.info("Terminating system")
      context.system.terminate()
  }

}

object WorkerActor {

  // Messages:
  case object Start
  case object RegisteredAck
  case object Terminate

  def props(supervisorPath: String) : Props = Props(classOf[WorkerActor], supervisorPath)

}