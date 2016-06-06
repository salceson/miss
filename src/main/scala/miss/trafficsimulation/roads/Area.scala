package miss.trafficsimulation.roads

import akka.actor.ActorRef
import com.typesafe.config.Config
import miss.trafficsimulation.actors.AreaActor.{AvailableRoadspaceInfo, OutgoingTrafficInfo}
import miss.trafficsimulation.roads.LightsDirection.{Horizontal, LightsDirection, Vertical}
import miss.trafficsimulation.roads.RoadDirection.{NS, RoadDirection, SN}
import miss.trafficsimulation.traffic.{Car, VehicleIdGenerator}
import miss.trafficsimulation.util.Color

import scala.Array.ofDim
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

case class AreaRoadDefinition(roadId: RoadId, direction: RoadDirection, outgoingActorRef: ActorRef, prevAreaActorRef: ActorRef)

class Area(verticalRoadsDefs: List[AreaRoadDefinition],
           horizontalRoadsDefs: List[AreaRoadDefinition],
           config: Config) {

  private val areaConfig = config.getConfig("trafficsimulation.area")

  private val roadSegmentsLength = areaConfig.getInt("cells_between_intersections")
  private val lanesNum = areaConfig.getInt("lanes")
  private val trafficDensity = areaConfig.getDouble("traffic_density")
  private val greenLightDuration = areaConfig.getInt("green_light_duration")

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

  val outgoingActorsAndRoadIds: List[(ActorRef, RoadId)] = (horizontalRoadsDefs ++ verticalRoadsDefs)
    .map((ard: AreaRoadDefinition) => (ard.outgoingActorRef, ard.roadId))

  val prevActorsAndRoadIds: List[(ActorRef, RoadId)] = (horizontalRoadsDefs ++ verticalRoadsDefs)
    .map((ard: AreaRoadDefinition) => (ard.prevAreaActorRef, ard.roadId))

  private val roadsMap = Map((horizontalRoads ++ verticalRoads).map((r: Road) => r.id -> r): _*)

  initTraffic()

  implicit val incomingTrafficOrdering = new Ordering[OutgoingTrafficInfo] {
    override def compare(x: OutgoingTrafficInfo, y: OutgoingTrafficInfo): Int = -x.timeframe.compare(y.timeframe)
  }

  private val incomingTrafficQueue = mutable.PriorityQueue[OutgoingTrafficInfo]()

  var intersectionGreenLightsDirection: LightsDirection = Horizontal

  private var _currentTimeFrame = 0: Long

  def currentTimeFrame: Long = _currentTimeFrame

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
    val nextAreaRoadSegment = NextAreaRoadSegment(roadDef.roadId, roadDef.outgoingActorRef, lanesNum)
    val lastSegment = new RoadSegment(roadDef.roadId, lanesNum,
      (roadSegmentsLength * 0.5).toInt, Some(lastIntersection), nextAreaRoadSegment,
      roadDef.direction, maxVelocity, maxAcceleration)
    if (horizontal) {
      lastIntersection.horizontalRoadOut = lastSegment
    } else {
      lastIntersection.verticalRoadOut = lastSegment
    }
    roadElems += lastIntersection
    roadElems += lastSegment

    new Road(roadDef.roadId, roadDef.direction, roadElems.toList, roadDef.prevAreaActorRef, nextAreaRoadSegment)
  }

  def initTraffic() = {
    for (road <- roadsMap.values) {
      for (roadElem <- road.elems) {
        roadElem match {
          case rs: RoadSegment =>
            for (lane <- rs.lanes) {
              for (cell <- lane.cells) {
                if (Random.nextFloat() <= trafficDensity) {
                  cell.vehicle = Some(Car(
                    id = VehicleIdGenerator.nextId,
                    maxVelocity = maxVelocity,
                    maxAcceleration = maxAcceleration,
                    color = Color.random,
                    timeFrame = 0
                  ))
                }
              }
            }
          case _ =>
        }
      }
    }
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

    if (timeFrame % greenLightDuration == 0) {
      intersectionGreenLightsDirection =
        if (intersectionGreenLightsDirection == Horizontal) Vertical else Horizontal
    }

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

  def putIncomingTraffic(msg: OutgoingTrafficInfo): Map[RoadId, Long] = {
    incomingTrafficQueue += msg

    val updatedRoads = mutable.Map[RoadId, Long]()

    while (incomingTrafficQueue.nonEmpty && incomingTrafficQueue.max.timeframe <= currentTimeFrame) {
      val trafficInfo@OutgoingTrafficInfo(roadId, timeFrame, outgoingTraffic) = incomingTrafficQueue.dequeue()
      val road = roadsMap(roadId)
      road.elems.head match {
        case firstRoadSeg: RoadSegment if timeFrame == firstRoadSeg.lastIncomingTrafficTimeFrame + 1 =>
          firstRoadSeg.putTraffic(timeFrame, outgoingTraffic, intersectionGreenLightsDirection)
          updatedRoads.put(road.id, timeFrame)
        case _: RoadSegment =>
          incomingTrafficQueue += trafficInfo
          return updatedRoads.toMap
        case _ => throw new ClassCastException
      }
    }

    updatedRoads.toMap
  }

  def updateNeighboursAvailableRoadspace(msg: AvailableRoadspaceInfo): Unit = {
    val road = roadsMap(msg.roadId)
    road.nextAreaRoadSegment.update(msg.timeframe, msg.availableSpacePerLane)
  }

  def countCars(): Int = {
    var carsCounter = 0
    for (road <- horizontalRoads) {
      for (roadElem <- road.elems) {
        roadElem match {
          case roadSeg: RoadSegment =>
            for (vac <- roadSeg.vehicleIterator()) {
              carsCounter += 1
            }
          case _ =>
        }
      }
    }
    for (road <- verticalRoads) {
      for (roadElem <- road.elems) {
        roadElem match {
          case roadSeg: RoadSegment =>
            for (vac <- roadSeg.vehicleIterator()) {
              carsCounter += 1
            }
          case _ =>
        }
      }
    }

    carsCounter
  }

  def printVehiclesPos(): String = {
    var carsCounter = 0
    val msgBuilder = StringBuilder.newBuilder
    msgBuilder append "\nPrinting cars positions:\n"
    for (road <- horizontalRoads) {
      var i = 0
      for (roadElem <- road.elems) {
        roadElem match {
          case roadSeg: RoadSegment => {
            for (vac <- roadSeg.vehicleIterator()) {
              carsCounter += 1
              msgBuilder append "%s\t%17s\t%s\t%d\t%d\t%d\ttf: %d\n".format(vac.vehicle.id, vac.vehicle.color, road.id, i, vac.laneIdx, vac.cellIdx, vac.vehicle.timeFrame)
            }
            i += 1
          }
          case _ =>
        }
      }
    }
    for (road <- verticalRoads) {
      var i = 0
      for (roadElem <- road.elems) {
        roadElem match {
          case roadSeg: RoadSegment => {
            for (vac <- roadSeg.vehicleIterator()) {
              carsCounter += 1
              msgBuilder append "%s\t%17s\t%s\t%d\t%d\t%d\ttf: %d\n".format(vac.vehicle.id, vac.vehicle.color, road.id, i, vac.laneIdx, vac.cellIdx, vac.vehicle.timeFrame)
            }
            i += 1
          }
          case _ =>
        }
      }
    }

    msgBuilder append "\nTotal cars: " + carsCounter + "\n"
    msgBuilder.toString
  }

  def getAvailableSpaceInfo: Iterable[(ActorRef, RoadId, List[Int])] = {
    roadsMap.values.map(road => {
      (
        road.prevAreaActorRef,
        road.id,
        (road.elems.head match {
          case firstRoadSeg: RoadSegment =>
            (0 until firstRoadSeg.lanesCount).map(i => firstRoadSeg.availableCells(i))
          case _ => throw new ClassCastException
        }).toList
        )
    })
  }

  def getAvailableSpaceInfo(roadId: RoadId): (ActorRef, RoadId, List[Int]) = {
    val road = roadsMap(roadId)
    (
      road.prevAreaActorRef,
      road.id,
      (road.elems.head match {
        case firstRoadSeg: RoadSegment =>
          (0 until firstRoadSeg.lanesCount).map(i => firstRoadSeg.availableCells(i))
        case _ => throw new ClassCastException
      }).toList
    )
  }
}
