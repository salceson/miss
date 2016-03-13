package miss.trafficsimulation.roads

import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.traffic.Vehicle

class Road(val id: RoadId, val direction: RoadDirection, val elems: List[RoadElem])

case class RoadId(id: Int)

trait RoadElem

class Intersection extends RoadElem {
  var horizontalRoadIn: RoadSegment = null
  var horizontalRoadOut: RoadSegment = null
  var verticalRoadIn: RoadSegment = null
  var verticalRoadOut: RoadSegment = null
}

class RoadSegment(val lanesCount: Int, val laneLength: Int, val in: Option[RoadElem], val out: RoadElem) extends RoadElem {
  val lanes: List[Lane] = List.fill(lanesCount)(new Lane(laneLength))
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
