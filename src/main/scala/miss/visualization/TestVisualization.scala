package miss.visualization

import miss.trafficsimulation.roads._
import miss.trafficsimulation.traffic.{Car, VehicleId}
import miss.trafficsimulation.util.CommonColors._
import miss.visualization.VisualizationActor.TrafficState

import scala.concurrent.duration._
import scala.language.postfixOps

trait TestRoads {
  val h02seg = new RoadSegment(RoadId(1), 3, 10, None, null, RoadDirection.EW, 5, 1)
  val h01seg = new RoadSegment(RoadId(1), 3, 10, None, null, RoadDirection.EW, 5, 1)
  val h00seg = new RoadSegment(RoadId(1), 3, 10, None, null, RoadDirection.EW, 5, 1)

  val h12seg = new RoadSegment(RoadId(2), 3, 10, None, null, RoadDirection.WE, 5, 1)
  val h11seg = new RoadSegment(RoadId(2), 3, 10, None, null, RoadDirection.WE, 5, 1)
  val h10seg = new RoadSegment(RoadId(2), 3, 10, None, null, RoadDirection.WE, 5, 1)

  val v02seg = new RoadSegment(RoadId(3), 3, 10, None, null, RoadDirection.NS, 5, 1)
  val v01seg = new RoadSegment(RoadId(3), 3, 10, None, null, RoadDirection.NS, 5, 1)
  val v00seg = new RoadSegment(RoadId(3), 3, 10, None, null, RoadDirection.NS, 5, 1)

  val v12seg = new RoadSegment(RoadId(4), 3, 10, None, null, RoadDirection.SN, 5, 1)
  val v11seg = new RoadSegment(RoadId(4), 3, 10, None, null, RoadDirection.SN, 5, 1)
  val v10seg = new RoadSegment(RoadId(4), 3, 10, None, null, RoadDirection.SN, 5, 1)

  val intersection1 = new Intersection
  val intersection2 = new Intersection
  val intersection3 = new Intersection
  val intersection4 = new Intersection

  val h0road = new Road(RoadId(1), RoadDirection.EW, List(h00seg, intersection4, h01seg, intersection1, h02seg), null)
  val h1road = new Road(RoadId(2), RoadDirection.WE, List(h10seg, intersection2, h11seg, intersection3, h12seg), null)
  val v0road = new Road(RoadId(3), RoadDirection.NS, List(v00seg, intersection1, v01seg, intersection2, v02seg), null)
  val v1road = new Road(RoadId(4), RoadDirection.SN, List(v10seg, intersection3, v11seg, intersection4, v12seg), null)

  val hRoads = List(h0road, h1road)
  val vRoads = List(v0road, v1road)
}

object TestVisualization extends Visualization {
  val trafficState1 = {
    val testRoads = new TestRoads {
      h00seg.lanes(1).cells(1).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v00seg.lanes(2).cells(0).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h10seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h00seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState2 = {
    val testRoads = new TestRoads {
      h01seg.lanes(1).cells(0).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v00seg.lanes(2).cells(3).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h11seg.lanes(2).cells(0).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h01seg.lanes(2).cells(2).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
      h00seg.lanes(0).cells(1).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState3 = {
    val testRoads = new TestRoads {
      h01seg.lanes(0).cells(4).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v00seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h11seg.lanes(1).cells(4).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h01seg.lanes(1).cells(6).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
      h00seg.lanes(1).cells(3).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState4 = {
    val testRoads = new TestRoads {
      h01seg.lanes(0).cells(8).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v00seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h11seg.lanes(1).cells(8).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h01seg.lanes(1).cells(8).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
      h01seg.lanes(1).cells(1).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState5 = {
    val testRoads = new TestRoads {
      v01seg.lanes(0).cells(2).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v00seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h12seg.lanes(1).cells(2).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h02seg.lanes(1).cells(1).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
      h01seg.lanes(2).cells(5).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState6 = {
    val testRoads = new TestRoads {
      v01seg.lanes(0).cells(6).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v01seg.lanes(2).cells(2).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h12seg.lanes(2).cells(4).vehicle = Some(Car(VehicleId(3), 2, 1, Red, 0))
      h02seg.lanes(1).cells(4).vehicle = Some(Car(VehicleId(4), 2, 1, Blue, 0))
      h01seg.lanes(2).cells(9).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState7 = {
    val testRoads = new TestRoads {
      h11seg.lanes(2).cells(0).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v01seg.lanes(1).cells(6).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h01seg.lanes(2).cells(9).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState8 = {
    val testRoads = new TestRoads {
      h11seg.lanes(1).cells(4).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v02seg.lanes(1).cells(1).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h01seg.lanes(2).cells(9).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState9 = {
    val testRoads = new TestRoads {
      h11seg.lanes(1).cells(9).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
      v02seg.lanes(0).cells(4).vehicle = Some(Car(VehicleId(2), 2, 1, Green, 0))
      h02seg.lanes(2).cells(2).vehicle = Some(Car(VehicleId(5), 2, 1, Magenta, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState10 = {
    val testRoads = new TestRoads {
      h12seg.lanes(1).cells(3).vehicle = Some(Car(VehicleId(1), 2, 1, Yellow, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState11 = {
    val testRoads = new TestRoads {
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  implicit val executor = system.dispatcher

  system.scheduler.scheduleOnce(1 seconds, actor, trafficState1)
  system.scheduler.scheduleOnce(3 seconds, actor, trafficState2)
  system.scheduler.scheduleOnce(5 seconds, actor, trafficState3)
  system.scheduler.scheduleOnce(7 seconds, actor, trafficState4)
  system.scheduler.scheduleOnce(9 seconds, actor, trafficState5)
  system.scheduler.scheduleOnce(11 seconds, actor, trafficState6)
  system.scheduler.scheduleOnce(13 seconds, actor, trafficState7)
  system.scheduler.scheduleOnce(15 seconds, actor, trafficState8)
  system.scheduler.scheduleOnce(17 seconds, actor, trafficState9)
  system.scheduler.scheduleOnce(19 seconds, actor, trafficState10)
  system.scheduler.scheduleOnce(21 seconds, actor, trafficState11)
}
