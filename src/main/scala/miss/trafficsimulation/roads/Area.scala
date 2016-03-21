package miss.trafficsimulation.roads

import com.typesafe.config.Config
import miss.trafficsimulation.roads.RoadDirection.RoadDirection

import scala.Array.ofDim
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection,
                              previousRoadElem: RoadIn, nextRoadElem: RoadElem)

class Area(verticalRoadsDefs: List[AreaRoadDefinition],
           horizontalRoadsDefs: List[AreaRoadDefinition],
           config: Config) {

  private val areaConfig = config.getConfig("area")

  private val roadSegmentsLength = areaConfig.getInt("cells_between_intersections")
  private val lanesNum = areaConfig.getInt("lanes")

  private val intersections = ofDim[Intersection](verticalRoadsDefs.size, horizontalRoadsDefs.size)

  for (x <- verticalRoadsDefs.indices; y <- horizontalRoadsDefs.indices) {
    intersections(x)(y) = new Intersection()
  }
  private val transposedIntersections = intersections.transpose

  private[roads] val horizontalRoads = horizontalRoadsDefs.indices.map(
    (y: Int) => createRoad(horizontalRoadsDefs(y), intersections(y).toList)
  )
  private[roads] val verticalRoads = verticalRoadsDefs.indices.map(
    (x: Int) => createRoad(verticalRoadsDefs(x), transposedIntersections(x).toList)
  )

  /**
    * Creates road for given definition and list of intersections.
    *
    * @param roadDef       Road definition
    * @param intersections List of intersection ordered from left to right
    * @return road created from definition and intersections
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
    val firstSegment = new RoadSegment(roadDef.roadId, lanesNum,
      roadSegmentsLength, None, firstIntersection)
    if (horizontal) {
      firstIntersection.horizontalRoadIn = firstSegment
    } else {
      firstIntersection.verticalRoadIn = firstSegment
    }
    roadElems += firstSegment

    //segments between intersections
    for (x <- 1 until orderedIntersections.size) {
      val prevIntersection = orderedIntersections(x - 1)
      val nextIntersection = orderedIntersections(x)
      val segment = new RoadSegment(roadDef.roadId, lanesNum,
        roadSegmentsLength, Some(prevIntersection), nextIntersection)
      if (horizontal) {
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
    val lastSegment = new RoadSegment(roadDef.roadId, lanesNum,
      roadSegmentsLength, Some(lastIntersection), roadDef.nextRoadElem)
    if (horizontal) {
      lastIntersection.horizontalRoadOut = lastSegment
    } else {
      lastIntersection.verticalRoadOut = lastSegment
    }
    roadElems += lastIntersection
    roadElems += lastSegment

    val road = new Road(roadDef.roadId, roadDef.direction, roadElems.toList)
    roadElems foreach {
      case rs: RoadSegment => rs.road = road
    }
    road
  }

  def simulate(): List[VehicleAndCoordinates] = {
    val vehiclesAndCoordinatesOutOfArea = ListBuffer[VehicleAndCoordinates]()

    val segmentsQueue = mutable.Queue[RoadElem]()
    segmentsQueue.enqueue(horizontalRoads.map((road: Road) => road.elems.reverse.head): _*)
    segmentsQueue.enqueue(verticalRoads.map((road: Road) => road.elems.reverse.head): _*)

    val segmentsDone = mutable.Map[RoadElem, Boolean](
      (horizontalRoads.flatMap((road: Road) => road.elems).map((roadElem: RoadElem) => roadElem -> false)
        ++ verticalRoads.flatMap((road: Road) => road.elems).map((roadElem: RoadElem) => roadElem -> false)): _*
    )

    while (segmentsQueue.nonEmpty) {
      val segment = segmentsQueue.dequeue()
      segment match {
        case rs: RoadSegment =>
          vehiclesAndCoordinatesOutOfArea ++= rs.simulate()
          val nextRoadSegment = rs.in
        case i: Intersection =>
        case _ =>
      }
    }
    vehiclesAndCoordinatesOutOfArea.toList
  }

}
