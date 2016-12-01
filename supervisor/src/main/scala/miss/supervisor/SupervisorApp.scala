package miss.supervisor

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

object SupervisorApp extends App {

  import Supervisor.Start

  val config = ConfigFactory.load()
  checkEnoughCores(config)
  val actorSystem = ActorSystem("TrafficSimulation", config)
  val supervisor = actorSystem.actorOf(Supervisor.props(config), "Supervisor")
  supervisor ! Start

  private def checkEnoughCores(config: Config) = {
    val cols = config.getInt("trafficsimulation.city.cols")
    val rows = config.getInt("trafficsimulation.city.rows")

    val nodes = config.getInt("worker.nodes")
    val cores = config.getInt("worker.cores")

    val requiredCores = cols * rows + cols + rows // area actors + boundary actors
    val totalCores = nodes * cores

    if (requiredCores > totalCores) {
      throw new IllegalArgumentException("Not enough cores for provided simulation settings.")
    }
  }
}
