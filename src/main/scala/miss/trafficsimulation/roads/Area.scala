package miss.trafficsimulation.roads

import miss.trafficsimulation.roads.RoadDirection.RoadDirection

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, previousRoadElem: RoadElem, nextRoadElem: RoadElem)

class Area(verticalRoads: List[AreaRoadDefinition],
           horizontalRoads: List[AreaRoadDefinition]) {

}
