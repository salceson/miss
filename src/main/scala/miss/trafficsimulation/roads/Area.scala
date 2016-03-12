package miss.trafficsimulation.roads

import com.typesafe.config.Config

class Area(config: Config) {
  private val areaConfig = config.getConfig("area")
  private val roadsNum = areaConfig.getInt("roads")
  private val roadIds = (1 to roadsNum).toList
  val roads: List[Road] = roadIds map {n => new Road(RoadId(n), List())}
}
