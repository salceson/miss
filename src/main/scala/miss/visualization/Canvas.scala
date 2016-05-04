package miss.visualization

import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.roads.LightsDirection.LightsDirection
import miss.trafficsimulation.roads.{Road, RoadSegment}
import miss.trafficsimulation.traffic.{Vehicle, VehicleId}
import miss.trafficsimulation.util.Color._
import miss.trafficsimulation.util._

import scala.collection.immutable.HashMap
import scala.swing.{Component, Dimension, Graphics2D, Point}

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

  var currentPositions = new HashMap[VehicleId, (Vehicle, Int, Int)]
  var prevPositions = new HashMap[VehicleId, (Vehicle, Int, Int)]

  preferredSize = new Dimension(canvasSize, canvasSize)

  override protected def paintComponent(g: Graphics2D): Unit = {
    g.setBackground(White)
    drawRoads(g)
    moveCar(g, new Point(5, 10), new Point(6, 17), Yellow)
    moveCar(g, new Point(5, 15), new Point(5, 17), Red)

    moveCar(g, new Point(2, 20), new Point(5, 23), Green)
    moveCar(g, new Point(3, 20), new Point(7, 21), Red)
    moveCar(g, new Point(3, 18), new Point(8, 18), Cyan)

    moveCar(g, new Point(17, 5), new Point(14, 5), Magenta)
    moveCar(g, new Point(16, 6), new Point(12, 6), Blue)
    moveCar(g, new Point(21, 5), new Point(14, 6), Green)
  }

  private def drawRoads(g: Graphics2D): Unit = {
    //horizontal roads
    g.setColor(getColorFromHTMLHex("#999999"))
    for (i <- 0 until areaSize) {
      g.fillRect(0, cellSize * ((0.5 * roadSegSize).toInt + i * (roadSegSize + lanesCount)), canvasSize, cellSize * lanesCount)
    }

    //vertical roads
    for (i <- 0 until areaSize) {
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

  def updateTraffic(horizontalRoads: List[Road],
                    verticalRoads: List[Road],
                    intersectionGreenLightsDirection: LightsDirection): Unit = {

    for ((road, i) <- horizontalRoads.view.zipWithIndex) {
      for ((roadSeg, j) <- road.elems.collect({ case r: RoadSegment => r }).view.zipWithIndex) {
        for (vac <- roadSeg.vehicleIterator()) {
          val y = (0.5 * roadSegSize).toInt + i * (roadSegSize + lanesCount) + (lanesCount - vac.laneIdx - 1)
          var x = vac.cellIdx
          if (j > 0) {
            x += x + (0.5 * roadSegSize).toInt
          }
          if (j > 1) {
            x += (j - 1) * (roadSegSize + lanesCount)
          }
          x = areaSize * (roadSegSize + lanesCount) - 1 - x


        }
      }
    }

    for ((road, i) <- verticalRoads.view.zipWithIndex) {

    }
  }
}
