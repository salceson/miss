package miss.trafficsimulation.roads

import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.traffic.MoveDirection._
import miss.trafficsimulation.traffic.{Move, Vehicle}

class Road(val id: RoadId, val direction: RoadDirection, val elems: List[RoadElem])

case class RoadId(id: Int)

trait RoadElem

class Intersection extends RoadElem {
  var horizontalRoadIn: RoadSegment = null
  var horizontalRoadOut: RoadSegment = null
  var verticalRoadIn: RoadSegment = null
  var verticalRoadOut: RoadSegment = null
}

case class VehicleAndCoordinates(vehicle: Vehicle, laneIdx: Long, cellIdx: Long)

class RoadSegment(val roadId: RoadId, val lanesCount: Int, val laneLength: Int,
                  val in: Option[RoadElem], val out: RoadElem) extends RoadElem {
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
