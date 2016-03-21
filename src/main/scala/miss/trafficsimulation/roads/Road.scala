package miss.trafficsimulation.roads

import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.traffic.MoveDirection._
import miss.trafficsimulation.traffic.{Move, Vehicle}

import scala.collection.mutable.ListBuffer

class Road(val id: RoadId, val direction: RoadDirection, val elems: List[RoadElem])

case class RoadId(id: Int)

sealed trait RoadElem

class Intersection extends RoadElem {
  var horizontalRoadIn: RoadSegment = null
  var horizontalRoadOut: RoadSegment = null
  var verticalRoadIn: RoadSegment = null
  var verticalRoadOut: RoadSegment = null

  def oppositeRoadSegment(roadSegment: RoadSegment) = {
    if (horizontalRoadIn == roadSegment) {
      horizontalRoadOut
    } else if (horizontalRoadOut == roadSegment) {
      horizontalRoadIn
    } else if (verticalRoadIn == roadSegment) {
      verticalRoadOut
    } else {
      verticalRoadIn
    }
  }
}

case class VehicleAndCoordinates(vehicle: Vehicle, laneIdx: Int, cellIdx: Int)

class RoadSegment(val roadId: RoadId,
                  val lanesCount: Int,
                  val laneLength: Int,
                  val in: Option[RoadElem],
                  val out: RoadElem,
                  var road: Road = null) extends RoadElem {

  val lanes: List[Lane] = List.fill(lanesCount)(new Lane(laneLength))

  private[roads] def vehicleIterator(): Iterator[VehicleAndCoordinates] = {
    val cellIndicesIterator = (0 until laneLength).reverseIterator
    cellIndicesIterator flatMap { cellIdx: Int =>
      val laneIndicesIterator = (0 until lanesCount).reverseIterator
      laneIndicesIterator flatMap { laneIdx: Int =>
        lanes(laneIdx).cells(cellIdx).vehicle match {
          case Some(vehicle) => Iterator(VehicleAndCoordinates(vehicle, laneIdx, cellIdx))
          case None => Iterator.empty
        }
      }
    }
  }

  private[roads] def calculatePossibleMoves(vehicleAndCoordinates: VehicleAndCoordinates): List[Move] = {
    List(Move(GoStraight, 0, 0))
  }

  def simulate(): List[VehicleAndCoordinates] = {
    val vehiclesAndCoordinatesOutOfArea = ListBuffer[VehicleAndCoordinates]()

    for (vac <- vehicleIterator()) {
      val move = vac.vehicle.move(calculatePossibleMoves(vac))
      val oldLaneIdx = vac.laneIdx
      val oldCellIdx = vac.cellIdx
      val cells = move.cellsCount
      var newLaneIdx = oldLaneIdx
      val gotToNewSegment = (oldCellIdx + cells) > laneLength
      val newCellIdx = (oldCellIdx + cells) % laneLength
      move.direction match {
        case TurnLeft | TurnRight =>
          newLaneIdx = move.laneIdx
        case SwitchLaneLeft =>
          newLaneIdx -= 1
        case SwitchLaneRight =>
          newLaneIdx += 1
        case GoStraight =>
      }
      lanes(oldLaneIdx).cells(oldCellIdx).vehicle = None
      if (gotToNewSegment) {
        out match {
          case i: Intersection =>
            val oppositeRoadSegment = i.oppositeRoadSegment(this)
            oppositeRoadSegment.lanes(newLaneIdx).cells(newCellIdx).vehicle = Some(vac.vehicle)
          case null =>
            vehiclesAndCoordinatesOutOfArea += vac.copy(laneIdx = newLaneIdx, cellIdx = newCellIdx)
        }
      } else {
        lanes(newLaneIdx).cells(newCellIdx).vehicle = Some(vac.vehicle)
      }
    }

    vehiclesAndCoordinatesOutOfArea.toList
  }
}

class Lane(val length: Int) {
  val cells: List[RoadCell] = List.fill(length)(new RoadCell)
}

class RoadCell {
  var vehicle: Option[Vehicle] = None
}

object RoadDirection extends Enumeration {
  type RoadDirection = Value
  val NS, SN, WE, EW = Value
}

trait RoadIn

trait RoadOut
