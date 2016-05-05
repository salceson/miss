package miss.visualization

import miss.trafficsimulation.roads._
import miss.trafficsimulation.traffic.{Car, VehicleId}
import miss.trafficsimulation.util.Yellow
import miss.visualization.VisualizationActor.TrafficState

import scala.concurrent.duration._

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

  val h0road = new Road(RoadId(1), RoadDirection.EW, List(h00seg, intersection4, h01seg, intersection1, h02seg))
  val h1road = new Road(RoadId(2), RoadDirection.WE, List(h10seg, intersection2, h11seg, intersection3, h12seg))
  val v0road = new Road(RoadId(3), RoadDirection.NS, List(v00seg, intersection1, v01seg, intersection2, v02seg))
  val v1road = new Road(RoadId(4), RoadDirection.SN, List(v10seg, intersection3, v11seg, intersection4, v12seg))
  
  val hRoads = List(h0road, h1road)
  val vRoads = List(v0road, v1road)
}

object TestVisualization extends Visualization{
  val trafficState1 = {
    val testRoads = new TestRoads {
      h00seg.lanes(0).cells(1).vehicle = Some(Car(VehicleId("1"), 2, 1, Yellow, 0))
    }
    
    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  val trafficState2 = {
    val testRoads = new TestRoads {
      h00seg.lanes(0).cells(6).vehicle = Some(Car(VehicleId("1"), 2, 1, Yellow, 0))
    }

    TrafficState(testRoads.hRoads, testRoads.vRoads, LightsDirection.Horizontal)
  }

  system.scheduler.scheduleOnce(5 seconds, actor, trafficState1)
  system.scheduler.scheduleOnce(10 seconds, actor, trafficState2)
}
