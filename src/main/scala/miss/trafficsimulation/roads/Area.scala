package miss.trafficsimulation.roads

import com.typesafe.config.Config
import miss.trafficsimulation.roads.RoadDirection.RoadDirection

import scala.Array.ofDim
import scala.collection.mutable.ListBuffer

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, previousRoadElem: RoadIn, nextRoadElem: RoadElem)

class Area(verticalRoadsDefs: List[AreaRoadDefinition],
           horizontalRoadsDefs: List[AreaRoadDefinition],
           config: Config) {

  private val areaConfig = config.getConfig("area")
  private val roadsNum = areaConfig.getInt("roads")

  private val roadSegmentsLength = areaConfig.getInt("cells_between_intersections")
  private val lanesNum = areaConfig.getInt("lanes")

  private val intersections = ofDim[Intersection](verticalRoadsDefs.size, horizontalRoadsDefs.size)

  private val verticalRoads = List[Road]()
  private val horizontalRoads = List[Road]()

  for (x <- verticalRoadsDefs.indices) {
    for (y <- horizontalRoadsDefs.indices) {
      intersections(x)(y) = new Intersection()
    }
  }

  for (y <- horizontalRoadsDefs.indices) {
    //TODO
    //horizontalRoads += createRoad(horizontalRoadsDefs(y), <<list of intersections in row y>>)
  }

  for (x <- verticalRoadsDefs.indices) {
    //TODO
    //verticalRoads += createRoad(verticalRoadsDefs(x), <<list of intersections in column x>>)
  }

  /**
    * Creates road for given definition and list of intersections.
    *
    * @param roadDef Road definition
    * @param intersections List of intersection ordered from left to right
    * @return
    */
  private def createRoad(roadDef: AreaRoadDefinition, intersections: List[Intersection]): Road = {
    val roadElems = ListBuffer[RoadElem]()

    val orderedIntersections = roadDef.direction match {
      case RoadDirection.NS | RoadDirection.WE => intersections
      case RoadDirection.SN | RoadDirection.EW => intersections.reverse
    }

    val horizontal = roadDef.direction == RoadDirection.EW || roadDef.direction == RoadDirection.WE

    //first segment
    val firstIntersection = orderedIntersections.head
    val firstSegment = new RoadSegment(lanesNum, roadSegmentsLength, None, firstIntersection)
    if(horizontal) {
      firstIntersection.horizontalRoadIn = firstSegment
    } else {
      firstIntersection.verticalRoadIn = firstSegment
    }
    roadElems += firstSegment

    //segments between intersections
    for (x <- 1 until orderedIntersections.size) {
      val prevIntersection = orderedIntersections(x - 1)
      val nextIntersection = orderedIntersections(x)
      val segment = new RoadSegment(lanesNum, roadSegmentsLength, Some(prevIntersection), nextIntersection)
      if(horizontal) {
        prevIntersection.horizontalRoadOut = segment
        nextIntersection.horizontalRoadIn = segment
      } else {
        prevIntersection.verticalRoadOut = segment
        nextIntersection.verticalRoadIn = segment
      }

      roadElems += prevIntersection
      roadElems += segment
    }

    //last segment
    val lastIntersection = orderedIntersections.last
    val lastSegment = new RoadSegment(lanesNum, roadSegmentsLength, Some(lastIntersection), roadDef.nextRoadElem)
    if(horizontal) {
      lastIntersection.horizontalRoadOut = lastSegment
    } else {
      lastIntersection.verticalRoadOut = lastSegment
    }
    roadElems += lastIntersection
    roadElems += lastSegment

    new Road(roadDef.roadId, roadDef.direction, roadElems.toList)
  }

}
