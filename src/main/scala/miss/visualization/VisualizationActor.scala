package miss.visualization

import akka.actor.{Actor, Props}
import miss.trafficsimulation.roads.LightsDirection.LightsDirection
import miss.trafficsimulation.roads.Road

class VisualizationActor(canvas: Canvas) extends Actor {

  import VisualizationActor._

  override def receive: Receive = {
    case TrafficState(horizontalRoads, verticalRoads, intersectionGreenLightsDirection) =>
      canvas.updateTraffic(horizontalRoads, verticalRoads, intersectionGreenLightsDirection)
  }
}

object VisualizationActor {

  def props(canvas: Canvas): Props = Props(classOf[VisualizationActor], canvas)

  case class TrafficState(horizontalRoads: List[Road],
                          verticalRoads: List[Road],
                          intersectionGreenLightsDirection: LightsDirection)

}
