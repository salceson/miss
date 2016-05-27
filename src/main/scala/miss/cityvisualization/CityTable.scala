package miss.cityvisualization

import java.awt.Dimension

import scala.Array.ofDim
import scala.swing.{BoxPanel, GridBagPanel, GridPanel, Label, Orientation}

class CityTable(rows: Int, cols: Int) extends GridPanel(rows, cols) {
  val cells = ofDim[CityCellWrapper](rows, cols)

  for (i <- 0 until rows) {
    for (j <- 0 until cols) {
      cells(i)(j) = new CityCellWrapper(i, j)
    }
  }

  preferredSize = new Dimension(800, 600)
  visible = true
  contents ++= cells.flatten.toSeq

  def updateTimeFrame(row: Int, col: Int, newTimeFrame: Long): Unit = {
    cells(row)(col).updateTimeFrame(newTimeFrame)
  }
}

class CityCellWrapper(val x: Int, val y: Int) extends GridBagPanel {
  val cityCell = new CityCell(x, y)
  _contents += cityCell
  visible = true

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    cityCell.updateTimeFrame(newTimeFrame)
  }
}

class CityCell(val x: Int, val y: Int) extends BoxPanel(Orientation.Vertical) {
  val coordsText = new Label(s"Area ($x, $y)")
  val timeFrameText = new Label("Frame 0")

  contents ++= Seq(coordsText, timeFrameText)
  visible = true

  def updateTimeFrame(newTimeFrame: Long): Unit = {
    timeFrameText.text = s"Frame $newTimeFrame"
    repaint()
  }
}
