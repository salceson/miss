package miss.trafficsimulation.roads

import com.typesafe.config.ConfigFactory
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
  }
}
