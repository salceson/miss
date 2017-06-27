package miss.worker

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object WorkerApp extends App {

  private def startRemoteWorkerSystem() = {
    val config = ConfigFactory.load("worker")
    val supervisorHostname = config.getString("supervisor.hostname")
    val supervisorPath = s"akka.tcp://TrafficSimulation@$supervisorHostname:6666/user/Supervisor"

    val retryIntervalSeconds = config.getLong("supervisor.association.retry.interval.seconds")
    val supervisorAssociationTimeoutSeconds = config.getLong("supervisor.association.timeout.seconds")

    val system = ActorSystem("RemoteWorker", ConfigFactory.load("worker"))
    val worker = system.actorOf(WorkerActor.props(supervisorPath, retryIntervalSeconds, supervisorAssociationTimeoutSeconds), "worker")
    worker ! WorkerActor.Start
  }

  startRemoteWorkerSystem()
}
