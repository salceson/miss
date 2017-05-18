package miss.worker

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object WorkerApp extends App {

  private def startRemoteWorkerSystem() = {
    val config = ConfigFactory.load("worker")
    val supervisorHostname = config.getString("supervisor.hostname")
    val supervisorPort = config.getString("supervisor.port")
    val supervisorPath = s"akka.tcp://TrafficSimulation@$supervisorHostname:$supervisorPort/user/Supervisor"

    val retryIntervalSeconds = config.getLong("supervisor.association.retry.interval.seconds")

    val system = ActorSystem("RemoteWorker", ConfigFactory.load("worker"))
    val worker = system.actorOf(WorkerActor.props(supervisorPath, retryIntervalSeconds), "worker")
    worker ! WorkerActor.Start
  }

  startRemoteWorkerSystem()
}
