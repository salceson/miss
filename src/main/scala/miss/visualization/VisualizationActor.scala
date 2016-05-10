package miss.visualization

import akka.actor.{Actor, Props}
import miss.supervisor.Supervisor
import miss.trafficsimulation.roads.LightsDirection.LightsDirection
import miss.trafficsimulation.roads.Road

class VisualizationActor(canvas: Canvas) extends Actor {

  import VisualizationActor._
  import Supervisor._

  val supervisor = context.actorSelection("akka.tcp://TrafficSimulation@127.0.0.1:6666/user/Supervisor")

  override def receive: Receive = {
    case Init(x, y) =>
      supervisor ! StartVisualization(x, y)
    case TrafficState(horizontalRoads, verticalRoads, intersectionGreenLightsDirection) =>
      canvas.updateTraffic(horizontalRoads, verticalRoads, intersectionGreenLightsDirection)
    case Exit(x, y) =>
      supervisor ! StopVisualization(x, y)
  }
}

object VisualizationActor {

  def props(canvas: Canvas): Props = Props(classOf[VisualizationActor], canvas)

  case class TrafficState(horizontalRoads: List[Road],
                          verticalRoads: List[Road],
                          intersectionGreenLightsDirection: LightsDirection)

  case class Init(x: Int, y: Int)

  case class Exit(x: Int, y: Int)

}
