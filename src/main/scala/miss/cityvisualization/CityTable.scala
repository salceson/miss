package miss.cityvisualization

import java.awt.Dimension
import java.time.LocalDateTime

import akka.actor.ActorSystem
import miss.visualization.{Canvas, VisualizationActor}

import scala.Array.ofDim
import scala.collection.mutable.ListBuffer
import scala.swing.{Action, BoxPanel, Button, Frame, GridBagPanel, GridPanel, Label, Orientation}
import scala.util.Random

class CityTable(rows: Int, cols: Int, system: ActorSystem) extends GridPanel(rows, cols) {
  val cells = ofDim[CityCellWrapper](rows, cols)
  val last10SecondsMeasurements: ListBuffer[Int] = ListBuffer.fill(10)(0)
  val last10SecondsDates: ListBuffer[LocalDateTime] = ListBuffer.fill(10)(LocalDateTime.now())
  var currentTimeIndex: Int = 0
  var currentTime: Long = 0

  for (i <- 0 until rows) {
    for (j <- 0 until cols) {
      cells(i)(j) = new CityCellWrapper(i, j, system)
    }
  }

  preferredSize = new Dimension(800, 600)
  visible = true
  contents ++= cells.flatten.toSeq

  def updateTimeFrame(row: Int, col: Int, newTimeFrame: Long): Unit = {
    cells(row)(col).updateTimeFrame(newTimeFrame)
  }
}

class CityCellWrapper(val x: Int, val y: Int, system: ActorSystem) extends GridBagPanel {
  val cityCell = new CityCell(x, y, system)
  _contents += cityCell
  visible = true

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    cityCell.updateTimeFrame(newTimeFrame)
  }
}

class CityCell(val x: Int, val y: Int, system: ActorSystem) extends BoxPanel(Orientation.Vertical) {
  val coordsText = new Label(s"Area ($x, $y)")
  val timeFrameText = new Label("Frame 0")
  val button = new Button(new Action("View area") {
    override def apply() = {
      import VisualizationActor._

      val canvas: Canvas = new Canvas
      val randomInt = Random.nextInt()
      val actor = system.actorOf(VisualizationActor.props(canvas), s"visualizer_${x}_${y}_$randomInt")
      actor ! Init(x, y)

      new Frame() {
        contents = canvas
        title = s"Traffic Simulation Visualization: Area ($x, $y)"
        visible = true

        override def closeOperation(): Unit = {
          actor ! Exit(x, y)
          Thread.sleep(1000)
          super.closeOperation()
        }
      }
    }
  })

  contents ++= Seq(coordsText, timeFrameText, button)
  visible = true

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    timeFrameText.text = s"Frame $newTimeFrame"
    repaint()
  }
}
