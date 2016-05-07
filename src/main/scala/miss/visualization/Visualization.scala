package miss.visualization

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.swing._

/**
  * @author Michal Janczykowski
  */
trait Visualization extends SimpleSwingApplication {

  val config = ConfigFactory.load()
  val trafficSimulationConfig = config.getConfig("trafficsimulation")
  val areaConfig = trafficSimulationConfig.getConfig("area")
  val visConfig = trafficSimulationConfig.getConfig("visualization")

  val canvas = new Canvas
  val system = ActorSystem()
  val actor = system.actorOf(VisualizationActor.props(canvas), "vizActor")

  override def startup(args: Array[String]): Unit = {
    super.startup(args)
  }

  override def top: Frame = {
    val cellSize = visConfig.getInt("cell_size")
    val roadSegSize = areaConfig.getInt("cells_between_intersections")
    val areaSize = areaConfig.getInt("size")
    val lanesCount = areaConfig.getInt("lanes")

    new MainFrame {
      title = "Traffic Simulation Visualization"
      contents = canvas

      override def closeOperation(): Unit = {
        system.terminate()
        super.closeOperation()
      }
    }
  }
}

object Visualization extends Visualization
