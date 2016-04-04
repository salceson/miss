package miss.visualization

import miss.trafficsimulation.util._
import Color._

import com.typesafe.config.ConfigFactory

import scala.swing.{Point, Graphics2D, Dimension, Component}

/**
  * NOTE: all point coordinates are scaled by cellSize set in configuration
  *
  * @author Michal Janczykowski
  */
class Canvas extends Component {
  val config = ConfigFactory.load()
  val trafficSimulationConfig = config.getConfig("trafficsimulation")
  val areaConfig = trafficSimulationConfig.getConfig("area")
  val visConfig = trafficSimulationConfig.getConfig("visualization")

  val cellSize = visConfig.getInt("cell_size")
  val halfCellSize = (cellSize * 0.5).toInt

  val roadSegSize = areaConfig.getInt("cells_between_intersections")
  val areaSize = areaConfig.getInt("size")
  val lanesCount = areaConfig.getInt("lanes")

  val canvasSize = cellSize * (areaSize * roadSegSize + areaSize * lanesCount)

  preferredSize = new Dimension(canvasSize, canvasSize)

  override protected def paintComponent(g: Graphics2D): Unit = {
    g.setBackground(White)
    drawRoads(g)
    moveCar(g, new Point(5, 10), new Point(6, 17), Yellow)
    moveCar(g, new Point(5, 15), new Point(5, 17), Red)

    moveCar(g, new Point(10, 20), new Point(5, 23), Green)
    moveCar(g, new Point(11, 20), new Point(7, 21), Red)
    moveCar(g, new Point(8, 20), new Point(3, 20), Cyan)

    moveCar(g, new Point(17, 5), new Point(14, 5), Magenta)
    moveCar(g, new Point(16, 6), new Point(12, 6), Blue)
    moveCar(g, new Point(21, 5), new Point(14, 6), Green)
  }

  private def drawRoads(g: Graphics2D): Unit = {
    //horizontal roads
    g.setColor(getColorFromHTMLHex("#999999"))
    for(i <- 0 until areaSize) {
      g.fillRect(0, cellSize * ((0.5 * roadSegSize).toInt + i * (roadSegSize + lanesCount)), canvasSize, cellSize * lanesCount)
    }

    //vertical roads
    for(i <- 0 until areaSize) {
      g.fillRect(cellSize * ((0.5 * roadSegSize).toInt + i * (roadSegSize + lanesCount)), 0, cellSize * lanesCount, canvasSize)
    }
  }

  private def drawCar(g: Graphics2D, p: Point, c: Color): Unit = {
    g.setColor(c)
    g.fillRect(p.x * cellSize, p.y * cellSize, cellSize, cellSize)
  }

  private def moveCar(g: Graphics2D, from: Point, to: Point, c: Color): Unit = {
    drawCar(g, to, c)
    g.drawLine(from.x * cellSize + halfCellSize, from.y * cellSize + halfCellSize, to.x * cellSize + halfCellSize, to.y * cellSize + halfCellSize)
  }
}
