package miss.trafficsimulation.traffic

import miss.trafficsimulation.util.Color

import scala.util.Random

case class Situation()

case class VehicleId(id: String)

trait Vehicle {
  def id: VehicleId

  def move(possibleMoves: List[Move]): Move

  def color: Color
}

case class Car(id: VehicleId,
               maxVelocity: Int,
               maxAcceleration: Int,
               color: Color,
               var currentVelocity: Int = 0,
               var currentAcceleration: Int = 0)
  extends Vehicle {
  override def move(possibleMoves: List[Move]): Move = {
    currentAcceleration = Math.min(currentAcceleration + 1, maxAcceleration)

    val maximumPossibleVelocityInFrame =
      Math.min(maxVelocity, currentVelocity + currentAcceleration)

    val selectedMove = Random.shuffle(possibleMoves).head

    if (maximumPossibleVelocityInFrame > selectedMove.cellsCount) {
      currentAcceleration = 0
      currentVelocity = selectedMove.cellsCount
    } else {
      currentVelocity = maximumPossibleVelocityInFrame
    }

    selectedMove.copy(cellsCount = currentVelocity)
  }
}
