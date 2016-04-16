package miss.trafficsimulation.roads

import akka.actor.ActorRef
import com.typesafe.config.Config
import miss.trafficsimulation.roads.LightsDirection.{LightsDirection, Horizontal, Vertical}
import miss.trafficsimulation.roads.RoadDirection.{RoadDirection, NS, SN}

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

  private val vehicleConfig = config.getConfig("vehicle")
  private val maxVelocity = vehicleConfig.getInt("max_velocity")
  private val maxAcceleration = vehicleConfig.getInt("max_acceleration")

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

  private var intersectionGreenLightsDirection: LightsDirection = Horizontal

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
      roadSegmentsLength, None, firstIntersection, roadDef.direction,
      maxVelocity, maxAcceleration)
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
        roadSegmentsLength, Some(prevIntersection), nextIntersection, roadDef.direction,
        maxVelocity, maxAcceleration)
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
      roadSegmentsLength, Some(lastIntersection), roadDef.nextRoadElem, roadDef.direction,
      maxVelocity, maxAcceleration)
    if (horizontal) {
      lastIntersection.horizontalRoadOut = lastSegment
    } else {
      lastIntersection.verticalRoadOut = lastSegment
    }
    roadElems += lastIntersection
    roadElems += lastSegment

    new Road(roadDef.roadId, roadDef.direction, roadElems.toList)
  }

  def simulate(): List[(ActorRef, RoadId, VehicleAndCoordinates)] = {
    val vehiclesAndCoordinatesOutOfArea = ListBuffer[(ActorRef, RoadId, VehicleAndCoordinates)]()

    val segmentsQueue = mutable.Queue[RoadElem]()

    val allSegments = (
      horizontalRoads.flatMap((road: Road) => road.elems)
        ++ verticalRoads.flatMap((road: Road) => road.elems)
      ).filter((r: RoadElem) =>
      r match {
        case _: RoadSegment => true
        case _ => false
      })

    segmentsQueue.enqueue(allSegments: _*)

    val segmentsDone = mutable.Map[RoadElem, Boolean](
      allSegments.map((roadElem: RoadElem) => roadElem -> false): _*
    )

    while (segmentsQueue.nonEmpty) {
      val segment = segmentsQueue.dequeue().asInstanceOf[RoadSegment]
      if (canCalculate(segment, segmentsDone)) {
        vehiclesAndCoordinatesOutOfArea ++= segment.simulate(intersectionGreenLightsDirection)
        segmentsDone(segment) = true
      } else {
        segmentsQueue.enqueue(segment)
      }
    }

    intersectionGreenLightsDirection =
      if (intersectionGreenLightsDirection == Horizontal) Vertical else Horizontal
    vehiclesAndCoordinatesOutOfArea.toList
  }

  private def canCalculate(segment: RoadSegment, segmentsDone: mutable.Map[RoadElem, Boolean]): Boolean = {
    if (segment.out.isInstanceOf[NextAreaRoadSegment]) {
      true
    } else if (segment.roadDirection != intersectionGreenLightsDirection) {
      true
    } else {
      val intersection = segment.out.asInstanceOf[Intersection]
      segmentsDone(intersection.horizontalRoadOut) && segmentsDone(intersection.verticalRoadOut)
    }
  }

  private def roadDirectionToLightsDirection(roadDirection: RoadDirection): LightsDirection = {
    if (roadDirection == NS || roadDirection == SN) {
      Horizontal
    } else {
      Vertical
    }
  }

}
