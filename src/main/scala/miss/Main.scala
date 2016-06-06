package miss

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import miss.supervisor.Supervisor

object Main extends App {

  import miss.supervisor.Supervisor.Start

  val config = ConfigFactory.load()
  val actorSystem = ActorSystem("TrafficSimulation", config)
  val supervisor = actorSystem.actorOf(Supervisor.props(config), "Supervisor")
  supervisor ! Start
}
