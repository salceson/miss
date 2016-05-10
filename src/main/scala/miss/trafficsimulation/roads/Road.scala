package miss.trafficsimulation.roads

import akka.actor.ActorRef
import miss.trafficsimulation.roads.LightsDirection.{Horizontal, LightsDirection, Vertical}
import miss.trafficsimulation.roads.RoadDirection.{EW, NS, RoadDirection, SN, WE}
import miss.trafficsimulation.traffic.MoveDirection.{GoStraight, SwitchLaneLeft, SwitchLaneRight, Turn}
import miss.trafficsimulation.traffic.{Car, Move, Vehicle, VehicleId}

import scala.collection.mutable.ListBuffer

case class Road(id: RoadId, direction: RoadDirection, elems: List[RoadElem])

case class RoadId(id: Int)

sealed trait RoadElem

case class NextAreaRoadSegment(roadId: RoadId, actor: ActorRef) extends RoadElem

class Intersection extends RoadElem with Serializable {
  var horizontalRoadIn: RoadSegment = null
  var horizontalRoadOut: RoadSegment = null
  var verticalRoadIn: RoadSegment = null
  var verticalRoadOut: RoadSegment = null

  def oppositeRoadSegment(roadSegment: RoadSegment): RoadSegment = {
    if (horizontalRoadIn == roadSegment) {
      horizontalRoadOut
    } else if (horizontalRoadOut == roadSegment) {
      horizontalRoadIn
    } else if (verticalRoadIn == roadSegment) {
      verticalRoadOut
    } else {
      verticalRoadIn
    }
  }

  /**
    * Get road segment after turn.
    *
    * Valid only for one-way roads.
    *
    * @param roadSegment road segment to turn from
    * @return road segment to turn into
    */
  def turnRoadSegment(roadSegment: RoadSegment): RoadSegment = {
    if (horizontalRoadIn == roadSegment) {
      verticalRoadOut
    } else if (verticalRoadIn == roadSegment) {
      horizontalRoadOut
    } else {
      throw new IllegalArgumentException("Cannot turn from output road!")
    }
  }
}

case class VehicleAndCoordinates(vehicle: Vehicle, laneIdx: Int, cellIdx: Int)

class RoadSegment(val roadId: RoadId,
                  val lanesCount: Int,
                  val laneLength: Int,
                  val in: Option[RoadElem],
                  val out: RoadElem,
                  val roadDirection: RoadDirection,
                  val maxVelocity: Int,
                  val maxAcceleration: Int) extends RoadElem with Serializable {

  val lanes: List[Lane] = List.fill(lanesCount)(new Lane(laneLength))
  var currentTimeFrame = 0 : Long //last computed time frame
  var lastIncomingTrafficTimeFrame = 0 : Long

  var vehicleIdSequence = 0 : Long

  /**
    * Iterates through cars - but only at the specified timeFrame.
    *
    * @param timeFrame time frame number
    * @return cars in proper order
    */
  private[roads] def vehicleIterator(timeFrame: Long): Iterator[VehicleAndCoordinates] = {
    val cellIndicesIterator = (0 until laneLength).reverseIterator
    cellIndicesIterator flatMap { cellIdx: Int =>
      val laneIndicesIterator = (0 until lanesCount).reverseIterator
      laneIndicesIterator flatMap { laneIdx: Int =>
        lanes(laneIdx).cells(cellIdx).vehicle match {
          case Some(vehicle) if vehicle.timeFrame == timeFrame =>
            Iterator(VehicleAndCoordinates(vehicle, laneIdx, cellIdx))
          case _ => Iterator.empty
        }
      }
    }
  }

  //TODO: Check if needed
  // used in visualization
  def vehicleIterator(): Iterator[VehicleAndCoordinates] = {
    val cellIndicesIterator = (0 until laneLength).reverseIterator
    cellIndicesIterator flatMap { cellIdx: Int =>
      val laneIndicesIterator = (0 until lanesCount).reverseIterator
      laneIndicesIterator flatMap { laneIdx: Int =>
        lanes(laneIdx).cells(cellIdx).vehicle match {
          case Some(vehicle) => Iterator(VehicleAndCoordinates(vehicle, laneIdx, cellIdx))
          case None => Iterator.empty
        }
      }
    }
  }

  private[roads] def calculatePossibleMoves(vac: VehicleAndCoordinates,
                                            lightsDirection: LightsDirection): List[Move] = {
    val moves = ListBuffer[Move]()
    val distanceBeforeSegmentEnd = laneLength - vac.cellIdx - 1
    val isInFirstPartOfTheSegment = distanceBeforeSegmentEnd > maxVelocity
    if (isInFirstPartOfTheSegment) {
      moves ++= calculateMovesInFirstPart(vac, distanceBeforeSegmentEnd)
    } else {
      moves ++= calculateMovesInSecondPart(vac, distanceBeforeSegmentEnd, lightsDirection)
    }
    if (moves.isEmpty) {
      List(Move(GoStraight, vac.laneIdx, 0))
    } else {
      moves.toList
    }
  }

  private def calculateMovesInFirstPart(vac: VehicleAndCoordinates,
                                        distanceBeforeSegmentEnd: Int): List[Move] = {
    val possibleMoves = ListBuffer[Move]()

    //Go straight
    val maxPossibleCellsStraight = getMaxPossibleCellsInLane(
      vac.cellIdx + 1, vac.cellIdx + maxVelocity + 1, vac.laneIdx)
    if (maxPossibleCellsStraight > 0) {
      possibleMoves += Move(GoStraight, vac.laneIdx, maxPossibleCellsStraight)
    }

    //Switch lane left
    if (vac.laneIdx > 0) {
      val maxPossibleCellsSwitchLaneLeft = getMaxPossibleCellsInLane(
        vac.cellIdx + 1, vac.cellIdx + maxVelocity + 1, vac.laneIdx - 1)
      if (maxPossibleCellsSwitchLaneLeft > 0) {
        possibleMoves += Move(SwitchLaneLeft, vac.laneIdx - 1, maxPossibleCellsSwitchLaneLeft)
      }
    }

    //Switch lane right
    if (vac.laneIdx < lanesCount - 1) {
      val maxPossibleCellsSwitchLaneRight = getMaxPossibleCellsInLane(
        vac.cellIdx + 1, vac.cellIdx + maxVelocity + 1, vac.laneIdx + 1)
      if (maxPossibleCellsSwitchLaneRight > 0) {
        possibleMoves += Move(SwitchLaneRight, vac.laneIdx + 1, maxPossibleCellsSwitchLaneRight)
      }
    }

    possibleMoves.toList
  }

  private def calculateMovesInSecondPart(vac: VehicleAndCoordinates,
                                         distanceBeforeSegmentEnd: Int,
                                         lightsDirection: LightsDirection): List[Move] = {
    val possibleMoves = ListBuffer[Move]()
    val possibleStraightInThisSegment = getMaxPossibleCellsInLane(
      vac.cellIdx + 1, laneLength, vac.laneIdx)
    if (0 < possibleStraightInThisSegment && possibleStraightInThisSegment < distanceBeforeSegmentEnd) {
      possibleMoves += Move(GoStraight, vac.laneIdx, possibleStraightInThisSegment)
    } else if (areLightsRed(lightsDirection) && distanceBeforeSegmentEnd > 0) {
      possibleMoves +=
        Move(GoStraight, vac.laneIdx, Math.min(possibleStraightInThisSegment, distanceBeforeSegmentEnd))
    } else if (!areLightsRed(lightsDirection)) {
      out match {
        case _: NextAreaRoadSegment =>
          possibleMoves += Move(GoStraight, vac.laneIdx, possibleStraightInThisSegment + 1)
        case i: Intersection =>
          val nextSegment = i.oppositeRoadSegment(this)
          val turnSegment = i.turnRoadSegment(this)
          //Go straight
          val possibleStraightInNextSegment = nextSegment.getMaxPossibleCellsInLane(
            0, maxVelocity - possibleStraightInThisSegment, vac.laneIdx
          )
          if (possibleStraightInThisSegment + possibleStraightInNextSegment > 0) {
            possibleMoves += Move(GoStraight, vac.laneIdx,
              possibleStraightInThisSegment + possibleStraightInNextSegment)
          }
          //Turn left
          if (vac.laneIdx == 0) {
            val turnPossible = roadDirection match {
              case NS if turnSegment.roadDirection == WE => true
              case SN if turnSegment.roadDirection == EW => true
              case EW if turnSegment.roadDirection == NS => true
              case WE if turnSegment.roadDirection == SN => true
              case _ => false
            }
            if (turnPossible) {
              for (newLaneIdx <- 0 until lanesCount) {
                val possibleInTurnSegment = turnSegment.getMaxPossibleCellsInLane(
                  0, maxVelocity - possibleStraightInThisSegment, newLaneIdx
                )
                if (possibleInTurnSegment > 0) {
                  possibleMoves += Move(Turn, newLaneIdx,
                    possibleStraightInThisSegment + possibleInTurnSegment)
                }
              }
            }
          }
          //Turn right
          if (vac.laneIdx == lanesCount - 1) {
            val turnPossible = roadDirection match {
              case NS if turnSegment.roadDirection == EW => true
              case SN if turnSegment.roadDirection == WE => true
              case EW if turnSegment.roadDirection == SN => true
              case WE if turnSegment.roadDirection == NS => true
              case _ => false
            }
            if (turnPossible) {
              for (newLaneIdx <- 0 until lanesCount) {
                val possibleInTurnSegment = turnSegment.getMaxPossibleCellsInLane(
                  0, maxVelocity - possibleStraightInThisSegment, newLaneIdx
                )
                if (possibleInTurnSegment > 0) {
                  possibleMoves += Move(Turn, newLaneIdx,
                    possibleStraightInThisSegment + possibleInTurnSegment)
                }
              }
            }
          }
      }
    }
    possibleMoves.toList
  }

  private[roads] def getMaxPossibleCellsInLane(fromCellIdx: Int,
                                               toCellIdx: Int,
                                               laneIdx: Int): Int = {
    var isPossible = true
    var maxPossible = 0
    for (cellIdx <- fromCellIdx until toCellIdx) {
      if (lanes(laneIdx).cells(cellIdx).vehicle.isEmpty && isPossible) {
        maxPossible += 1
      } else {
        isPossible = false
      }
    }
    Math.min(maxPossible, maxVelocity)
  }

  def simulate(lightsDirection: LightsDirection, timeFrame: Long): List[(ActorRef, RoadId, VehicleAndCoordinates)] = {
    val vehiclesAndCoordinatesOutOfArea = ListBuffer[(ActorRef, RoadId, VehicleAndCoordinates)]()

    for (vac <- vehicleIterator(timeFrame)) {
      val move = vac.vehicle.move(calculatePossibleMoves(vac, lightsDirection))
      val oldLaneIdx = vac.laneIdx
      val oldCellIdx = vac.cellIdx
      val cells = move.cellsCount
      var newLaneIdx = oldLaneIdx
      val gotToNewSegment = (oldCellIdx + cells) >= laneLength
      val newCellIdx = (oldCellIdx + cells) % laneLength
      move.direction match {
        case Turn =>
          newLaneIdx = move.laneIdx
        case SwitchLaneLeft =>
          newLaneIdx -= 1
        case SwitchLaneRight =>
          newLaneIdx += 1
        case GoStraight =>
      }
      lanes(oldLaneIdx).cells(oldCellIdx).vehicle = None
      if (gotToNewSegment) {
        out match {
          case i: Intersection =>
            val nextRoadSegment = move.direction match {
              case GoStraight | SwitchLaneLeft | SwitchLaneRight => i.oppositeRoadSegment(this)
              case Turn => i.turnRoadSegment(this)
            }
            nextRoadSegment.lanes(newLaneIdx).cells(newCellIdx).vehicle = Some(vac.vehicle)
          case NextAreaRoadSegment(_, actor) =>
            val messageContents = (actor, roadId, vac.copy(laneIdx = newLaneIdx, cellIdx = newCellIdx))
            vehiclesAndCoordinatesOutOfArea += messageContents
        }
      } else {
        lanes(newLaneIdx).cells(newCellIdx).vehicle = Some(vac.vehicle)
      }
    }

    if (timeFrame > currentTimeFrame) {
      currentTimeFrame = timeFrame
    }

    vehiclesAndCoordinatesOutOfArea.toList
  }

  private def areLightsRed(lightsDirection: LightsDirection): Boolean = {
    if (roadDirection == NS || roadDirection == SN) {
      lightsDirection == Horizontal
    } else {
      lightsDirection == Vertical
    }
  }

  def availableCells(laneIdx: Int, limit: Int): Int = {
    val cells = lanes(laneIdx).cells
    for (i <- 0 to limit) {
      if(cells(i).vehicle.isDefined)
        return i - 1
    }
    limit
  }

  def putTraffic(incomingTrafficTimeFrame: Long, incomingTraffic: List[VehicleAndCoordinates], lightsDirection: LightsDirection): Unit = {
    lastIncomingTrafficTimeFrame = incomingTrafficTimeFrame

    for (VehicleAndCoordinates(vehicle, laneIdx, cellIdx) <- incomingTraffic) {
      val cellToPutId = Math.min(cellIdx, availableCells(laneIdx, cellIdx))
      val vehicleToPut = Car(getNextVehicleId, vehicle.maxVelocity, vehicle.maxAcceleration, vehicle.color, incomingTrafficTimeFrame, vehicle.currentVelocity, vehicle.currentAcceleration)
      lanes(laneIdx).cells(cellToPutId).vehicle = Some(vehicleToPut)
    }

    for (timeFrame <- (incomingTrafficTimeFrame + 1) until currentTimeFrame) {
      simulate(lightsDirection, timeFrame)
    }

  }

  private def getNextVehicleId: VehicleId = {
    val id = vehicleIdSequence
    vehicleIdSequence += 1
    VehicleId(id.toString)
  }
}

class Lane(val length: Int) extends Serializable {
  val cells: List[RoadCell] = List.fill(length)(new RoadCell)
}

class RoadCell extends Serializable {
  var vehicle: Option[Vehicle] = None
}

object RoadDirection extends Enumeration {
  type RoadDirection = Value
  val NS, SN, WE, EW = Value
}

object LightsDirection extends Enumeration {
  type LightsDirection = Value
  val Horizontal, Vertical = Value
}

trait RoadIn

trait RoadOut
