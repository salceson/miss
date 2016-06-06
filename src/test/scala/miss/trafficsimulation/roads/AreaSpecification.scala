package miss.trafficsimulation.roads

import com.typesafe.config.ConfigFactory
import miss.trafficsimulation.actors.AreaActor.OutgoingTrafficInfo
import miss.trafficsimulation.roads.RoadDirection._
import miss.trafficsimulation.traffic.{Car, VehicleId}
import miss.trafficsimulation.util.CommonColors._
import org.specs2.mutable.Specification

class AreaSpecification extends Specification {
  "Area" should {
    "create proper roads" in {
      val road1 = AreaRoadDefinition(RoadId(1), NS, null, null)
      val road2 = AreaRoadDefinition(RoadId(2), SN, null, null)
      val road3 = AreaRoadDefinition(RoadId(3), EW, null, null)
      val road4 = AreaRoadDefinition(RoadId(4), WE, null, null)
      val road5 = AreaRoadDefinition(RoadId(5), NS, null, null)
      val road6 = AreaRoadDefinition(RoadId(6), SN, null, null)
      val road7 = AreaRoadDefinition(RoadId(7), EW, null, null)
      val road8 = AreaRoadDefinition(RoadId(8), WE, null, null)

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

    "return lanes length as available space for empty first road segment" in {
      val area = createTestArea

      val expectedRoadSpaceInfoList = List(
        (null, RoadId(1), List(5, 5, 5)),
        (null, RoadId(2), List(5, 5, 5)),
        (null, RoadId(3), List(5, 5, 5)),
        (null, RoadId(4), List(5, 5, 5))
      )

      area.getAvailableSpaceInfo must containAllOf(expectedRoadSpaceInfoList)
    }

    "return available space for non-empty road segment" in {
      val area = createTestArea
      area.verticalRoads.head.elems.head match {
        case firstRoadSeg: RoadSegment =>
          firstRoadSeg.lanes(0).cells(0).vehicle = Some(Car(VehicleId(1), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(1).cells(1).vehicle = Some(Car(VehicleId(2), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(2).cells(2).vehicle = Some(Car(VehicleId(3), 10, 1, Red, 5, 0))
      }

      area.horizontalRoads.head.elems.head match {
        case firstRoadSeg: RoadSegment =>
          firstRoadSeg.lanes(0).cells(2).vehicle = Some(Car(VehicleId(4), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(0).cells(4).vehicle = Some(Car(VehicleId(5), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(1).cells(0).vehicle = Some(Car(VehicleId(6), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(1).cells(3).vehicle = Some(Car(VehicleId(7), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(2).cells(0).vehicle = Some(Car(VehicleId(8), 10, 1, Red, 5, 0))
          firstRoadSeg.lanes(2).cells(2).vehicle = Some(Car(VehicleId(9), 10, 1, Red, 5, 0))
      }

      val expectedRoadSpaceInfoList = List(
        (null, RoadId(1), List(0, 1, 2)),
        (null, RoadId(2), List(5, 5, 5)),
        (null, RoadId(3), List(2, 0, 0)),
        (null, RoadId(4), List(5, 5, 5))
      )

      area.getAvailableSpaceInfo must containAllOf(expectedRoadSpaceInfoList)
    }
  }

  private def createTestArea: Area = {
    val road1 = AreaRoadDefinition(RoadId(1), NS, null, null)
    val road2 = AreaRoadDefinition(RoadId(2), SN, null, null)
    val road3 = AreaRoadDefinition(RoadId(3), EW, null, null)
    val road4 = AreaRoadDefinition(RoadId(4), WE, null, null)

    val area = new Area(List(road1, road2),
      List(road3, road4), ConfigFactory.load("simple_area.conf"))

    area
  }

  private def createAnotherTestArea: Area = {
    val road1 = AreaRoadDefinition(RoadId(1), NS, null, null)
    val road2 = AreaRoadDefinition(RoadId(2), SN, null, null)
    val road3 = AreaRoadDefinition(RoadId(3), EW, null, null)
    val road4 = AreaRoadDefinition(RoadId(4), WE, null, null)

    val area = new Area(List(road1, road2),
      List(road3, road4), ConfigFactory.load("another_area.conf"))

    area
  }
}
