package miss.cityvisualization

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

trait CityVisualizer extends SimpleSwingApplication {

  import CityVisualizerActor._

  val config = ConfigFactory.load("city_visualization.conf")
  val system = ActorSystem("CityVisualization", config)
  val cols = config.getInt("trafficsimulation.city.cols")
  val rows = config.getInt("trafficsimulation.city.rows")
  val cityTable: CityTable = new CityTable(cols, rows)
  val actor = system.actorOf(CityVisualizerActor.props(cityTable), "cityViz")

  override val top: Frame = new MainFrame {
    title = "Traffic Simulation - City Visualization"
    contents = cityTable
    visible = true

    override def closeOperation(): Unit = {
      actor ! CityVisualizerStop
      Thread.sleep(1000)
      system.terminate()
      super.closeOperation()
    }
  }

  override def startup(args: Array[String]): Unit = {
    super.startup(args)
    actor ! CityVisualizerStart
  }
}

object CityVisualizerMain extends CityVisualizer
