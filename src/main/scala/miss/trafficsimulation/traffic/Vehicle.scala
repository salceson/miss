package miss.trafficsimulation.traffic

import miss.trafficsimulation.util.Color

case class Move()

case class Situation()

case class VehicleId(id: String)

trait Vehicle {
  def move(situation: Situation): Move
}

case class Car(id: VehicleId,
               maxVelocity: Int,
               maxAcceleration: Int,
               color: Color,
               var currentVelocity: Int = 0,
               var currentAcceleration: Int = 0)
  extends Vehicle {
  override def move(situation: Situation): Move = ???
}
