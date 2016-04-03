package miss.visualization

import com.typesafe.config.ConfigFactory

import swing._

/**
  * @author Michal Janczykowski
  */
object Visualization extends SimpleSwingApplication {

  val config = ConfigFactory.load()
  val trafficSimulationConfig = config.getConfig("trafficsimulation")
  val areaConfig = trafficSimulationConfig.getConfig("area")
  val visConfig = trafficSimulationConfig.getConfig("visualization")

  override def top: Frame = {
    val cellSize = visConfig.getInt("cell_size")
    val roadSegSize = areaConfig.getInt("cells_between_intersections")
    val areaSize = areaConfig.getInt("size")
    val lanesCount = areaConfig.getInt("lanes")

    new MainFrame {
      title = "Traffic Simulation Visualization"
      contents = new Canvas()
    }
  }
}
