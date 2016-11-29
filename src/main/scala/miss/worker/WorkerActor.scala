package miss.worker

import akka.actor.{FSM, Props}
import akka.remote.AssociationErrorEvent
import miss.supervisor.Supervisor.{RegisterWorker, UnregisterWorker}
import miss.worker.WorkerActor.{Data, State}

import scala.concurrent.duration._
import scala.language.postfixOps

class WorkerActor(supervisorPath: String, retryIntervalSeconds: Long) extends FSM[State, Data] {

  import WorkerActor._
  import context.dispatcher

  val supervisor = context.actorSelection(supervisorPath)

  startWith(Initial, EmptyData)

  when(Initial) {
    case Event(Start, _) =>
      log.info("Sending RegisterWorker to supervisor")
      context.system.eventStream.subscribe(self, classOf[AssociationErrorEvent])
      supervisor ! RegisterWorker
      stay
    case Event(e: AssociationErrorEvent, _) =>
      context.system.scheduler.scheduleOnce(retryIntervalSeconds seconds, self, Start)
      stay
    case Event(RegisterWorkerAck, _) =>
      log.info("Connected with Supervisor")
      context.system.eventStream.unsubscribe(self, classOf[AssociationErrorEvent])
      goto(Working)
  }

  when(Working) {
    case Event(Terminate, _) =>
      log.info("Sending UnregisterWorker to supervisor")
      supervisor ! UnregisterWorker
      context.system.scheduler.scheduleOnce(5 seconds, self, TerminateSystem)
      goto(Terminating)
  }

  when(Terminating) {
    case Event(TerminateSystem, _) =>
      log.info("Terminating system")
      context.system.terminate()
      stop
  }
}

object WorkerActor {

  // Messages:
  case object Start

  case object RegisterWorkerAck

  case object Terminate

  case object TerminateSystem

  def props(supervisorPath: String, retryIntervalSeconds: Long): Props = Props(classOf[WorkerActor], supervisorPath, retryIntervalSeconds)

  // State:
  sealed trait State

  case object Initial extends State

  case object Working extends State

  case object Terminating extends State

  // Data:
  sealed trait Data

  case object EmptyData extends Data

}