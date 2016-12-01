package miss.cityvisualization

import akka.actor._
import miss.supervisor.Supervisor

class CityVisualizerActor(val cityTable: CityTable) extends Actor {

  import CityVisualizerActor._
  import Supervisor._

  val supervisor = context.actorSelection("akka.tcp://TrafficSimulation@127.0.0.1:6666/user/Supervisor")

  override def receive: Receive = {
    case CityVisualizerStart =>
      supervisor ! CityVisualizationStartRequest
    case CityVisualizerStop =>
      supervisor ! CityVisualizationStopRequest
    case CityVisualizationUpdate(x, y, newTimeFrame) =>
      cityTable.updateTimeFrame(x, y, newTimeFrame)
  }
}

object CityVisualizerActor {

  case class CityVisualizationUpdate(x: Int, y: Int, newTimeFrame: Long)

  case object CityVisualizerStart

  case object CityVisualizerStop

  def props(cityTable: CityTable): Props = Props(classOf[CityVisualizerActor], cityTable)
}
