package miss

import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.roads.Area

object Main extends App {
  val config = ConfigFactory.load()
  val trafficSimulationConfig = config.getConfig("trafficsimulation")
//  val area = new Area(trafficSimulationConfig)
//  println(area)
}
