package miss.visualization

import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.roads.LightsDirection.LightsDirection
import miss.trafficsimulation.roads.{Road, RoadDirection, RoadSegment}
import miss.trafficsimulation.traffic.VehicleId
import miss.trafficsimulation.util.Color
import miss.trafficsimulation.util.Color._
import miss.trafficsimulation.util.CommonColors._

import scala.collection.mutable
import scala.swing.{Component, Dimension, Graphics2D, Point}

/**
  * NOTE: all point coordinates are scaled by cellSize set in configuration
  *
  * @author Michal Janczykowski
  */
class Canvas extends Component {
  val config = ConfigFactory.load("city_visualization.conf")

  val trafficSimulationConfig = config.getConfig("trafficsimulation")
  val areaConfig = trafficSimulationConfig.getConfig("area")
  val visConfig = trafficSimulationConfig.getConfig("visualization")
  val cellSize = visConfig.getInt("cell_size")

  val halfCellSize = (cellSize * 0.5).toInt
  val roadSegSize = areaConfig.getInt("cells_between_intersections")
  val halfRoadSegSize = (roadSegSize * 0.5).toInt

  val areaSize = areaConfig.getInt("size")
  val lanesCount = areaConfig.getInt("lanes")
  val areaCellSize = areaSize * (roadSegSize + lanesCount)
  val canvasSize = cellSize * (areaSize * roadSegSize + areaSize * lanesCount)

  @volatile var currentPositions = mutable.HashMap[VehicleId, VizVehicle]()
  @volatile var prevPositions = mutable.HashMap[VehicleId, VizVehicle]()

  preferredSize = new Dimension(canvasSize, canvasSize)

  override protected def paintComponent(g: Graphics2D): Unit = {
    g.setBackground(White)
    drawRoads(g)
    drawTraffic(g)
  }

  private def drawRoads(g: Graphics2D): Unit = {
    //horizontal roads
    g.setColor(getColorFromHTMLHex("#999999"))
    for (i <- 0 until areaSize) {
      g.fillRect(0, cellSize * (halfRoadSegSize + i * (roadSegSize + lanesCount)), canvasSize, cellSize * lanesCount)
    }

    //vertical roads
    for (i <- 0 until areaSize) {
      g.fillRect(cellSize * (halfRoadSegSize + i * (roadSegSize + lanesCount)), 0, cellSize * lanesCount, canvasSize)
    }
  }

  private def drawTraffic(g: Graphics2D): Unit = {
    for (vizVehicle <- currentPositions.values) {
      val currentPos = new Point(vizVehicle.x, vizVehicle.y)
      if (prevPositions.contains(vizVehicle.vehicleId)) {
        val prevVizVehicle = prevPositions(vizVehicle.vehicleId)
        val prevPos = new Point(prevVizVehicle.x, prevVizVehicle.y)

        moveCar(g, prevPos, currentPos, vizVehicle.color)
      } else {
        drawCar(g, currentPos, vizVehicle.color)
      }
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

    prevPositions = currentPositions
    val tempPositions = mutable.HashMap[VehicleId, VizVehicle]()

    for ((road, i) <- horizontalRoads.view.zipWithIndex) {
      for ((roadSeg, j) <- road.elems.collect({ case r: RoadSegment => r }).view.zipWithIndex) {
        for (vac <- roadSeg.vehicleIterator()) {
          var y = halfRoadSegSize + i * (roadSegSize + lanesCount)
          if (road.direction == RoadDirection.EW) {
            y += (lanesCount - vac.laneIdx - 1)
          } else {
            y += vac.laneIdx
          }

          var x = vac.cellIdx
          if (j > 0) {
            x += halfRoadSegSize + lanesCount
          }
          if (j > 1) {
            x += (j - 1) * (roadSegSize + lanesCount)
          }

          if (road.direction == RoadDirection.EW) {
            x = areaCellSize - 1 - x
          }

          tempPositions += vac.vehicle.id -> VizVehicle(vac.vehicle.id, vac.vehicle.color, x, y)
        }
      }
    }

    for ((road, i) <- verticalRoads.view.zipWithIndex) {
      for ((roadSeg, j) <- road.elems.collect({ case r: RoadSegment => r }).view.zipWithIndex) {
        for (vac <- roadSeg.vehicleIterator()) {
          var x = halfRoadSegSize + i * (roadSegSize + lanesCount)
          if (road.direction == RoadDirection.NS) {
            x += (lanesCount - vac.laneIdx - 1)
          } else {
            x += vac.laneIdx
          }

          var y = vac.cellIdx
          if (j > 0) {
            y += halfRoadSegSize + lanesCount
          }
          if (j > 1) {
            y += (j - 1) * (roadSegSize + lanesCount)
          }

          if (road.direction == RoadDirection.SN) {
            y = areaCellSize - 1 - y
          }

          tempPositions += vac.vehicle.id -> VizVehicle(vac.vehicle.id, vac.vehicle.color, x, y)
        }
      }
    }

    currentPositions = tempPositions
    repaint()
  }
}
