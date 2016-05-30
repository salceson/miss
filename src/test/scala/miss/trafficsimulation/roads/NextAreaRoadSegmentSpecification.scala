package miss.trafficsimulation.roads

import org.specs2.mutable.Specification

/**
  * @author Michal Janczykowski
  */
class NextAreaRoadSegmentSpecification extends Specification {
  "NextAreaRoadSegment" should {
    "do it's job" in {
      val instance = NextAreaRoadSegment(RoadId(0), null, 3)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beFalse
      instance.canSendCar(2) must beFalse

      instance.update(1, List(1, 3, 2))

      instance.canSendCar(0) must beTrue
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(1, 0)
      instance.sendCar(1, 1)
      instance.sendCar(1, 2)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(1, 1)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(2, 1)
      instance.sendCar(2, 2)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beFalse
      instance.canSendCar(2) must beFalse

      instance.update(2, List(0, 1, 1))

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.update(3, List(0, 1, 1))

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue
    }

    "handle messages in reversed order" in {
      val instance = NextAreaRoadSegment(RoadId(0), null, 3)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beFalse
      instance.canSendCar(2) must beFalse

      instance.update(1, List(1, 3, 2))

      instance.canSendCar(0) must beTrue
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(1, 0)
      instance.sendCar(1, 1)
      instance.sendCar(1, 2)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(1, 1)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.sendCar(2, 1)
      instance.sendCar(2, 2)

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beFalse
      instance.canSendCar(2) must beFalse

      instance.update(3, List(0, 1, 1))

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue

      instance.update(2, List(0, 0, 0))

      instance.canSendCar(0) must beFalse
      instance.canSendCar(1) must beTrue
      instance.canSendCar(2) must beTrue
    }
  }
}
