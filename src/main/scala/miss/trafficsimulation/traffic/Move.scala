package miss.trafficsimulation.traffic

import miss.trafficsimulation.traffic.MoveDirection.MoveDirection

case class Move(val direction: MoveDirection, val cellsCount: Int)

object MoveDirection extends Enumeration {
  type MoveDirection = Value
  val GoStraight, TurnLeft, TurnRight, SwitchLaneLeft, SwitchLaneRight = Value
}