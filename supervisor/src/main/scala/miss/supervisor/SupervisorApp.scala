package miss.supervisor

import akka.actor.ActorSystem
import akka.remote.DisassociatedEvent
import com.typesafe.config.{Config, ConfigFactory}

object SupervisorApp extends App {

  import Supervisor.Start

  val config = ConfigFactory.load("supervisor")
  checkEnoughCores(config)
  val actorSystem = ActorSystem("TrafficSimulation", config)
  val supervisor = actorSystem.actorOf(Supervisor.props(config), "Supervisor")
  actorSystem.eventStream.subscribe(supervisor, classOf[DisassociatedEvent])
  supervisor ! Start

  private def checkEnoughCores(config: Config) = {
    val cols = config.getInt("trafficsimulation.city.cols")
    val rows = config.getInt("trafficsimulation.city.rows")

    val nodes = config.getInt("worker.nodes")
    val cores = config.getInt("worker.areas_per_node")

    val requiredCores = cols * rows // area actors
    val totalCores = nodes * cores

    if (requiredCores > totalCores) {
      throw new IllegalArgumentException("Not enough cores for provided simulation settings.")
    }
  }
}
