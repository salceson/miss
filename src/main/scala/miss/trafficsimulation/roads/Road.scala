package miss.trafficsimulation.roads

import miss.trafficsimulation.traffic.Vehicle

class Road {

}

class Intersection(var horizontalRoadIn: RoadSegment,
                   var horizontalRoadOut: RoadSegment,
                   var verticalRoadIn: RoadSegment,
                   var verticalRoadOut: RoadSegment) {

}

class RoadSegment(lanesCount: Int, laneLength: Int) {
  var lanes: List[Lane] = List.fill(lanesCount)(new Lane(laneLength))
}

class Lane(length: Int) {
  var cells: List[RoadCell] = List.fill(length)(new RoadCell)
}

class RoadCell {
  var vehicle: Option[Vehicle] = None
}