package miss.trafficsimulation.roads

import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.actors.AreaActor.OutgoingTrafficInfo
import miss.trafficsimulation.roads.RoadDirection._
import org.specs2.mutable.Specification

class AreaSpecification extends Specification {
  "Area" should {
    "create proper roads" in {
      val road1 = AreaRoadDefinition(RoadId(1), NS, null)
      val road2 = AreaRoadDefinition(RoadId(2), SN, null)
      val road3 = AreaRoadDefinition(RoadId(3), EW, null)
      val road4 = AreaRoadDefinition(RoadId(4), WE, null)
      val road5 = AreaRoadDefinition(RoadId(5), NS, null)
      val road6 = AreaRoadDefinition(RoadId(6), SN, null)
      val road7 = AreaRoadDefinition(RoadId(7), EW, null)
      val road8 = AreaRoadDefinition(RoadId(8), WE, null)

      val area = new Area(List(road1, road2, road5, road6),
        List(road3, road4, road7, road8), ConfigFactory.load("road_creation.conf"))

      area.verticalRoads must have length 4
      area.horizontalRoads must have length 4
    }

    "simulate 2 time frames without incoming traffic data" in {
      val area = createTestArea

      // tf 1
      area.isReadyForComputation() must beTrue
      area.simulate()

      // tf 2
      area.isReadyForComputation() must beTrue
      area.simulate()

      // tf 3
      area.isReadyForComputation() must beFalse
    }

    "simulate more time frames after having received incoming traffic data" in {
      val area = createTestArea
      area.simulate() // tf 1
      area.simulate() // tf 2
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(1), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(2), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(3), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(4), 1, List()))
      area.isReadyForComputation() must beTrue

      area.simulate() // tf 3
      area.isReadyForComputation() must beFalse
    }

    "handle messages in mixed order" in {
      val area = createTestArea
      area.simulate() // tf 1
      area.simulate() // tf 2
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(1), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(2), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(3), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(4), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(1), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(2), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(3), 1, List()))
      area.isReadyForComputation() must beTrue

      area.simulate() // tf 3
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(4), 2, List()))
      area.isReadyForComputation() must beTrue

      area.simulate() // tf 4
      area.isReadyForComputation() must beFalse
    }

    "simulate for another data" in {
      val area = createAnotherTestArea
      area.simulate() // tf 1
      area.isReadyForComputation() must beTrue
      area.simulate() // tf 2
      area.isReadyForComputation() must beTrue
      area.simulate() // tf 3
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(1), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(2), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(3), 2, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(4), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(1), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(2), 1, List()))
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(3), 1, List()))
      area.isReadyForComputation() must beTrue

      area.simulate() // tf 4
      area.isReadyForComputation() must beFalse

      area.putIncomingTraffic(OutgoingTrafficInfo(RoadId(4), 2, List()))
      area.isReadyForComputation() must beTrue

      area.simulate() // tf 5
      area.isReadyForComputation() must beFalse
    }
  }

  private def createTestArea: Area = {
    val road1 = AreaRoadDefinition(RoadId(1), NS, null)
    val road2 = AreaRoadDefinition(RoadId(2), SN, null)
    val road3 = AreaRoadDefinition(RoadId(3), EW, null)
    val road4 = AreaRoadDefinition(RoadId(4), WE, null)

    val area = new Area(List(road1, road2),
      List(road3, road4), ConfigFactory.load("simple_area.conf"))

    area
  }

  private def createAnotherTestArea: Area = {
    val road1 = AreaRoadDefinition(RoadId(1), NS, null)
    val road2 = AreaRoadDefinition(RoadId(2), SN, null)
    val road3 = AreaRoadDefinition(RoadId(3), EW, null)
    val road4 = AreaRoadDefinition(RoadId(4), WE, null)

    val area = new Area(List(road1, road2),
      List(road3, road4), ConfigFactory.load("another_area.conf"))

    area
  }
}
