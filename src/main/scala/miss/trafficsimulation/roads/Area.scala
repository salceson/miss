package miss.trafficsimulation.roads

import akka.actor.ActorRef
import com.typesafe.config.Config
import miss.trafficsimulation.actors.AreaActor.OutgoingTrafficInfo
import miss.trafficsimulation.roads.LightsDirection.{Horizontal, LightsDirection, Vertical}
import miss.trafficsimulation.roads.RoadDirection.{NS, RoadDirection, SN}
import miss.trafficsimulation.traffic.{Car, VehicleId}
import miss.trafficsimulation.util.White

import scala.Array.ofDim
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, outgoingActorRef: ActorRef)

class Area(verticalRoadsDefs: List[AreaRoadDefinition],
           horizontalRoadsDefs: List[AreaRoadDefinition],
           config: Config) {

  private val areaConfig = config.getConfig("trafficsimulation.area")

  private val roadSegmentsLength = areaConfig.getInt("cells_between_intersections")
  private val lanesNum = areaConfig.getInt("lanes")

  private val vehicleConfig = config.getConfig("trafficsimulation.vehicle")
  private val maxVelocity = vehicleConfig.getInt("max_velocity")
  private val maxAcceleration = vehicleConfig.getInt("max_acceleration")

  private val maxTimeFrameDelay = (roadSegmentsLength * 0.5).toInt / maxVelocity

  private val intersections = ofDim[Intersection](verticalRoadsDefs.size, horizontalRoadsDefs.size)

  for (x <- verticalRoadsDefs.indices; y <- horizontalRoadsDefs.indices) {
    intersections(x)(y) = new Intersection()
  }
  private val transposedIntersections = intersections.transpose

  val horizontalRoads = horizontalRoadsDefs.indices.map(
    (y: Int) => createRoad(horizontalRoadsDefs(y), intersections(y).toList)
  )
  val verticalRoads = verticalRoadsDefs.indices.map(
    (x: Int) => createRoad(verticalRoadsDefs(x), transposedIntersections(x).toList)
  )

  val actorsAndRoadIds: List[(ActorRef, RoadId)] = (horizontalRoadsDefs ++ verticalRoadsDefs)
    .map((ard: AreaRoadDefinition) => (ard.outgoingActorRef, ard.roadId))

  private val roadsMap = Map((horizontalRoads ++ verticalRoads).map((r: Road) => r.id -> r): _*)

  implicit val incomingTrafficOrdering = new Ordering[OutgoingTrafficInfo] {
    override def compare(x: OutgoingTrafficInfo, y: OutgoingTrafficInfo): Int = -x.timeframe.compare(y.timeframe)
  }

  private val incomingTrafficQueue = mutable.PriorityQueue[OutgoingTrafficInfo]()

  var intersectionGreenLightsDirection: LightsDirection = Horizontal

  private var _currentTimeFrame = 0 : Long

  def currentTimeFrame : Long = _currentTimeFrame

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
      (roadSegmentsLength * 0.5).toInt, None, firstIntersection, roadDef.direction,
      maxVelocity, maxAcceleration)

    firstSegment.lanes(0).cells(0).vehicle = Some(Car(
      id = VehicleId(Random.nextString(20)),
      maxVelocity = maxVelocity,
      maxAcceleration = maxAcceleration,
      color = White,
      timeFrame = 0
    ))

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
      (roadSegmentsLength * 0.5).toInt, Some(lastIntersection), NextAreaRoadSegment(roadDef.roadId, roadDef.outgoingActorRef),
      roadDef.direction, maxVelocity, maxAcceleration)
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
    val timeFrame = currentTimeFrame + 1

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
        //TODO: Check available space
        vehiclesAndCoordinatesOutOfArea ++= segment.simulate(intersectionGreenLightsDirection, timeFrame)
        segmentsDone(segment) = true
      } else {
        segmentsQueue.enqueue(segment)
      }
    }

    _currentTimeFrame = timeFrame

    //TODO read green light duration from config
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

  // TODO: test
  /**
    * @return true if next (currentTimeFrame + 1) frame can be simulated
    */
  def isReadyForComputation(): Boolean = {
    for (road <- horizontalRoads ++ verticalRoads) {
      road.elems.head match {
        case firstRoadSeg: RoadSegment => if ((currentTimeFrame + 1) - firstRoadSeg.lastIncomingTrafficTimeFrame > maxTimeFrameDelay) {
          return false
        }
        case _ => throw new ClassCastException
      }
    }

    true
  }

  def putIncomingTraffic(msg: OutgoingTrafficInfo) : Unit = {
    incomingTrafficQueue += msg

    while (incomingTrafficQueue.nonEmpty && incomingTrafficQueue.max.timeframe <= currentTimeFrame) {
      val trafficInfo@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic) = incomingTrafficQueue.dequeue()
      val road = roadsMap(roadId)
      road.elems.head match {
        case firstRoadSeg: RoadSegment if timeFrame == firstRoadSeg.lastIncomingTrafficTimeFrame + 1 =>
          firstRoadSeg.putTraffic(timeFrame, outgoingTraffic, intersectionGreenLightsDirection)
        case _: RoadSegment => {
          incomingTrafficQueue += trafficInfo
          return
        }
        case _ => throw new ClassCastException
      }
    }
  }

}
