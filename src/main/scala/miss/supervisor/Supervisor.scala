package miss.supervisor

import akka.actor._
import com.typesafe.config.Config
import miss.trafficsimulation.actors._
import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.roads._

import scala.Array.ofDim
import scala.collection.mutable.ListBuffer

class Supervisor(config: Config) extends Actor {

  import AreaActor._
  import Supervisor._

  private val cols = config.getInt("trafficsimulation.city.cols")
  private val rows = config.getInt("trafficsimulation.city.rows")
  private val size = config.getInt("trafficsimulation.area.size")
  private val verticalRoadsNum = cols * size
  private val horizontalRoadsNum = rows * size

  private val actors = ofDim[ActorRef](rows, cols)
  private val horizontalBoundaryActors = ofDim[ActorRef](rows)
  private val verticalBoundaryActors = ofDim[ActorRef](cols)
  private val horizontalRoadDefs = ofDim[RoadDefinition](horizontalRoadsNum)
  private val verticalRoadDefs = ofDim[RoadDefinition](verticalRoadsNum)

  override def receive: Receive = {
    case Start =>
      // Create area actors
      for (i <- 0 until rows) {
        for (j <- 0 until cols) {
          actors(i)(j) = context.actorOf(Props(classOf[AreaActor]), s"AreaActor_${i}_$j")
        }
      }
      // Create horizontal boundary actors
      for (i <- 0 until rows) {
        if (i % 2 == 0) {
          horizontalBoundaryActors(i) = context.actorOf(
            BoundaryAreaActor.props(actors(i)(0), actors(i)(cols - 1)),
            s"BoundaryActor_horizontal_$i"
          )
        } else {
          horizontalBoundaryActors(i) = context.actorOf(
            BoundaryAreaActor.props(actors(i)(cols - 1), actors(i)(0)),
            s"BoundaryActor_horizontal_$i"
          )
        }
      }
      // Create vertical boundary actors
      for (j <- 0 until cols) {
        if (j % 2 == 0) {
          verticalBoundaryActors(j) = context.actorOf(
            BoundaryAreaActor.props(actors(rows - 1)(j), actors(0)(j)),
            s"BoundaryActor_vertical_$j"
          )
        } else {
          verticalBoundaryActors(j) = context.actorOf(
            BoundaryAreaActor.props(actors(0)(j), actors(rows - 1)(j)),
            s"BoundaryActor_vertical_$j"
          )
        }
      }
      // Generate area roads definitions
      for (i <- 0 until verticalRoadsNum) {
        val direction = if (i % 2 == 0) RoadDirection.NS else RoadDirection.SN
        verticalRoadDefs(i) = RoadDefinition(RoadId(i), direction)
      }
      for (j <- 0 until horizontalRoadsNum) {
        val direction = if (j % 2 == 0) RoadDirection.EW else RoadDirection.WE
        horizontalRoadDefs(j) = RoadDefinition(RoadId(verticalRoadsNum + j), direction)
      }
      // Start simulation
      for (i <- 0 until rows) {
        for (j <- 0 until cols) {
          val areaVerticalRoadDefs = ListBuffer[AreaRoadDefinition]()
          val areaHorizontalRoadDefs = ListBuffer[AreaRoadDefinition]()

          for (vertRoad <- (i * size) until (i * size + size)) {
            if (i == 0) {
              areaVerticalRoadDefs +=
                verticalRoadDefs(vertRoad).toAreaRoadDefinition(verticalBoundaryActors(j))
            } else {
              areaVerticalRoadDefs +=
                verticalRoadDefs(vertRoad).toAreaRoadDefinition(actors(i - 1)(j))
            }
          }

          for (horRoad <- (j * size) until (j * size + size)) {
            if (j == 0) {
              areaHorizontalRoadDefs +=
                horizontalRoadDefs(horRoad).toAreaRoadDefinition(horizontalBoundaryActors(i))
            } else {
              areaHorizontalRoadDefs +=
                horizontalRoadDefs(horRoad).toAreaRoadDefinition(actors(i)(j - 1))
            }
          }

          actors(i)(j) ! StartSimulation(areaVerticalRoadDefs.toList, areaHorizontalRoadDefs.toList)
        }
      }
    case StartVisualization(x, y) =>
      val visualizer = sender()
      actors(x)(y) ! VisualizationStartRequest(visualizer)
    case StopVisualization(x, y) =>
      val visualizer = sender()
      actors(x)(y) ! VisualizationStopRequest(visualizer)
  }
}

object Supervisor {

  case object Start

  case class StartVisualization(x: Int, y: Int)

  case class StopVisualization(x: Int, y: Int)

  def props(config: Config): Props = Props(classOf[Supervisor], config)

}

case class RoadDefinition(roadId: RoadId, direction: RoadDirection) {
  def toAreaRoadDefinition(actorRef: ActorRef): AreaRoadDefinition =
    AreaRoadDefinition(roadId, direction, actorRef)
}
