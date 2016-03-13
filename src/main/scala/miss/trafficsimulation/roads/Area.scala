package miss.trafficsimulation.roads

import com.typesafe.config.Config
import miss.trafficsimulation.roads.RoadDirection.RoadDirection

import scala.Array.ofDim

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, previousRoadElem: RoadIn, nextRoadElem: RoadOut)

class Area(verticalRoads: List[AreaRoadDefinition],
           horizontalRoads: List[AreaRoadDefinition],
           config: Config) {

  private val areaConfig = config.getConfig("area")
  private val roadsNum = areaConfig.getInt("roads")

  private val roadSegmentsLength = areaConfig.getInt("cells_between_intersections")
  private val lanesNum = areaConfig.getInt("lanes")

  private val intersections = ofDim[Intersection](verticalRoads.size, horizontalRoads.size)

  for (x <- verticalRoads.indices) {
    for (y <- verticalRoads.indices) {
      intersections(x)(y) = new Intersection(null, null, null, null)
    }
  }

}
