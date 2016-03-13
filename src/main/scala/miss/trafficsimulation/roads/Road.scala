package miss.trafficsimulation.roads

import miss.trafficsimulation.traffic.Vehicle

class Road(val id: RoadId, val elems: List[RoadElem])

case class RoadId(id: Int)

trait RoadElem

class Intersection(val horizontalRoadIn: RoadSegment,
                   val horizontalRoadOut: RoadSegment,
                   val verticalRoadIn: RoadSegment,
                   val verticalRoadOut: RoadSegment) extends RoadElem

class RoadSegment(val lanesCount: Int, val laneLength: Int) extends RoadElem {
  val lanes: List[Lane] = List.fill(lanesCount)(new Lane(laneLength))
  var out: RoadElem = null
  var in: RoadElem = null
}

class Lane(val length: Int) {
  val cells: List[RoadCell] = List.fill(length)(new RoadCell)
}

class RoadCell {
  var vehicle: Option[Vehicle] = None
}
