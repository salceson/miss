package miss.trafficsimulation.traffic

import miss.trafficsimulation.util.{CommonColors, Color}
import org.specs2.mutable.Specification

class CarSpecification extends Specification {
  "Car" should {
    "move by one field straight if it isn't moving before" in {
      val car = Car(VehicleId(1), 10, 1, CommonColors.White, 0, 0)

      val possibleMoves = List(Move(MoveDirection.GoStraight, 0, 5))
      val move = car.move(possibleMoves)

      move must beEqualTo(Move(MoveDirection.GoStraight, 0, 1))
    }

    "turn and move by one field if it isn't moving before" in {
      val car = Car(VehicleId(1), 10, 1, CommonColors.White, 0, 0)

      val possibleMoves = List(Move(MoveDirection.Turn, 0, 5), Move(MoveDirection.Turn, 1, 3), Move(MoveDirection.Turn, 2, 5))
      val move = car.move(possibleMoves)

      val expectedMoves = List(Move(MoveDirection.Turn, 0, 1), Move(MoveDirection.Turn, 1, 1), Move(MoveDirection.Turn, 2, 1))

      expectedMoves must contain(move)
    }
  }
}
