package miss.trafficsimulation.traffic

import miss.trafficsimulation.traffic.MoveDirection.MoveDirection

/**
  * Represents a car's move.
  *
  * @param direction  move direction
  * @param cellsCount either available cells for car (if passed to car) or moved cells
  *                   (if returned from car)
  */
case class Move(direction: MoveDirection, laneIdx: Int, cellsCount: Int)

object MoveDirection extends Enumeration {
  type MoveDirection = Value
  val GoStraight, Turn, SwitchLaneLeft, SwitchLaneRight = Value
}
