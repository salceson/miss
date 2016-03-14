package miss.trafficsimulation.roads

import miss.trafficsimulation.traffic.{VehicleId, Car}
import miss.trafficsimulation.util._
import org.specs2.mutable.Specification

class RoadSegmentSpecification extends Specification {
  "RoadSegment" should {
    "iterate through cars in proper way" in {
      val roadSegment = new RoadSegment(RoadId(1), 3, 7, None, null)
      roadSegment.lanes(0).cells(6).vehicle = Some(Car(VehicleId("1"), 2, 1, Yellow))
      roadSegment.lanes(1).cells(5).vehicle = Some(Car(VehicleId("2"), 2, 1, Magenta))
      roadSegment.lanes(2).cells(5).vehicle = Some(Car(VehicleId("3"), 2, 1, Cyan))
      roadSegment.lanes(0).cells(3).vehicle = Some(Car(VehicleId("4"), 2, 1, Red))
      roadSegment.lanes(2).cells(3).vehicle = Some(Car(VehicleId("5"), 2, 1, Green))
      roadSegment.lanes(1).cells(2).vehicle = Some(Car(VehicleId("6"), 2, 1, Blue))
      roadSegment.lanes(2).cells(0).vehicle = Some(Car(VehicleId("7"), 2, 1, Black))

      val actual = roadSegment.vehicleIterator().toList.map(
        (vac: VehicleAndCoordinates) => vac.vehicle.color)
      val expected = List(Yellow, Cyan, Magenta, Green, Red, Blue, Black)

      actual mustEqual expected
    }
  }
}
