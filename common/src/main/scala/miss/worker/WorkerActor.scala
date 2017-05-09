package miss.worker

import akka.actor.{ FSM, Props }
import akka.remote.{ AssociationErrorEvent, DisassociatedEvent }
import miss.common.SerializableMessage
import miss.supervisor.Supervisor.RegisterWorker
import miss.worker.WorkerActor.{ Data, State }
import pl.edu.agh.iet.akka_tracing.TracedActor

import scala.concurrent.duration._
import scala.language.postfixOps

class WorkerActor(supervisorPath: String, retryIntervalSeconds: Long)
  extends FSM[State, Data] with TracedActor {

  import WorkerActor._
  import context.dispatcher

  val supervisor = context.actorSelection(supervisorPath)

  startWith(Initial, EmptyData)

  when(Initial) {
    case Event(Start, _) =>
      log.info("Sending RegisterWorker to supervisor")
      context.system.eventStream.subscribe(self, classOf[AssociationErrorEvent])
      context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
      supervisor ! RegisterWorker
      stay
    case Event(e: AssociationErrorEvent, _) =>
      context.system.scheduler.scheduleOnce(retryIntervalSeconds seconds, self, Start)
      stay
    case Event(RegisterWorkerAck, _) =>
      log.info("Connected with Supervisor")
      goto(Working)
  }

  when(Working) {
    case Event(Terminate, _) =>
      log.info("Got Terminate.")
      context.system.scheduler.scheduleOnce(5 seconds, self, TerminateSystem)
      context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
      goto(Terminating)
    case Event(e: DisassociatedEvent, _) =>
      log.error(s"Got DisassociatedEvent: ${e.toString}. Shutting down.")
      context.system.terminate()
      stop
    case Event(e: AssociationErrorEvent, _) =>
      log.error(s"Got AssociationErrorEvent: ${e.toString}. Shutting down.")
      context.system.terminate()
      stop
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

  case object RegisterWorkerAck extends SerializableMessage

  case object Terminate extends SerializableMessage

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