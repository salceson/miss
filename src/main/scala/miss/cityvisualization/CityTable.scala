package miss.cityvisualization

import java.awt.Dimension
import java.util.Date

import akka.actor.ActorSystem
import miss.visualization.{Canvas, VisualizationActor}

import scala.Array.ofDim
import scala.swing.{Action, BoxPanel, Button, Frame, GridBagPanel, GridPanel, Label, Orientation}
import scala.util.Random

class CityTableWrapper(val rows: Int,
                       val cols: Int,
                       val system: ActorSystem)
  extends BoxPanel(Orientation.Vertical) {

  val counter = new SpeedCounter(system)

  val table = new CityTable(rows, cols, system, this)
  val label = new Label()

  contents ++= Seq(table, label)
  visible = true

  updateSpeedLabel(0)
  var lastUpdated = new Date()

  def message(): Unit = {
    counter.message()
    val now = new Date()
    if (now.getTime - lastUpdated.getTime >= 1 * 1000) {
      lastUpdated = now
      val count = counter.calculateSpeed() / (rows * cols)
      updateSpeedLabel(count)
    }
  }

  def updateSpeedLabel(count: Double): Unit = {
    label.text = s"Mean FPS (calculated from last 10 seconds): $count"
    repaint()
  }
}

class CityTable(rows: Int,
                cols: Int,
                system: ActorSystem,
                wrapper: CityTableWrapper)
  extends GridPanel(rows, cols) {

  val cells = ofDim[CityCellWrapper](rows, cols)

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
    wrapper.message()
  }
}

class CityCellWrapper(val x: Int,
                      val y: Int,
                      val system: ActorSystem)
  extends GridBagPanel {

  val cityCell = new CityCell(x, y, system)
  _contents += cityCell
  visible = true

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    cityCell.updateTimeFrame(newTimeFrame)
  }
}

class CityCell(val x: Int,
               val y: Int,
               val system: ActorSystem)
  extends BoxPanel(Orientation.Vertical) {

  val counter = new SpeedCounter(system)
  val coordsText = new Label(s"Area ($x, $y)")

  val timeFrameText = new Label("Frame: 0")
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

  val fpsLabel = new Label()
  var lastUpdated = new Date

  contents ++= Seq(coordsText, timeFrameText, fpsLabel, button)
  visible = true

  updateSpeedLabel(0)

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    counter.message()
    timeFrameText.text = s"Frame: $newTimeFrame"
    repaint()
    val now = new Date()
    if (now.getTime - lastUpdated.getTime >= 1 * 1000) {
      lastUpdated = now
      val count = counter.calculateSpeed()
      updateSpeedLabel(count)
    }
  }

  def updateSpeedLabel(count: Double): Unit = {
    fpsLabel.text = s"FPS: $count"
    repaint()
  }
}
