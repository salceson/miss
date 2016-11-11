package miss.supervisor

import akka.actor._
import akka.remote.RemoteScope
import com.typesafe.config.Config
import miss.cityvisualization.CityVisualizerActor
import miss.supervisor.Supervisor.{Data, State}
import miss.trafficsimulation.actors._
import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.roads._

import scala.Array.ofDim
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Supervisor(config: Config) extends FSM[State, Data] {

  import AreaActor.{StartSimulation, VisualizationStartRequest, VisualizationStopRequest}
  import CityVisualizerActor._
  import Supervisor._

  private val cols = config.getInt("trafficsimulation.city.cols")
  private val rows = config.getInt("trafficsimulation.city.rows")
  private val size = config.getInt("trafficsimulation.area.size")
  private val verticalRoadsNum = cols * size
  private val horizontalRoadsNum = rows * size

  private val workerNodesCount = config.getInt("worker.nodes")
  private val workerCores = config.getInt("worker.cores")

  startWith(Initial, EmptyData)

  when(Initial) {
    case Event(Start, EmptyData) =>
      log.warning("Waiting for workers")
      goto(WaitingForWorkers) using WorkersData(List[ActorRef]())
  }

  when(WaitingForWorkers) {
    case Event(RegisterWorker, w@WorkersData(workers)) =>
      log.warning(sender().path.address.toString)

      val newWorkers = sender() :: workers

      if (allWorkersRegistered(newWorkers)) {
        val areaActors = startSimulation(newWorkers)
        goto(Working) using SupervisorData(newWorkers, areaActors, None)
      }
      else {
        goto(WaitingForWorkers) using WorkersData(newWorkers)
      }
  }

  when(Working) {
    case Event(StartVisualization(x, y), SupervisorData(_, actors, _)) =>
      val visualizer = sender()
      actors(x)(y) ! VisualizationStartRequest(visualizer)
      stay
    case Event(StopVisualization(x, y), SupervisorData(_, actors, _)) =>
      val visualizer = sender()
      actors(x)(y) ! VisualizationStopRequest(visualizer)
      stay
    case Event(CityVisualizationStartRequest, SupervisorData(workers, areaActors, _)) =>
      goto(Working) using SupervisorData(workers, areaActors, Some(sender()))
    case Event(CityVisualizationStopRequest, SupervisorData(workers, areaActors, _)) =>
      goto(Working) using SupervisorData(workers, areaActors, None)
    case Event(TimeFrameUpdate(x, y, newTimeFrame), SupervisorData(_, _, cityVisualizer)) =>
      cityVisualizer.foreach(ar => ar ! CityVisualizationUpdate(x, y, newTimeFrame))
      stay
  }

  private def allWorkersRegistered(workers: List[ActorRef]): Boolean = {
    workers.size == workerNodesCount
  }

  private def buildWorkersPool(workers: List[ActorRef]): mutable.Queue[Address] = {
    val workersPool = mutable.Queue[Address]()

    workers.foreach(worker => {
      val address = worker.path.address

      for (node <- 0 until workerNodesCount) {
        for (cores <- 0 until workerCores) {
          workersPool += address
        }
      }
    })

    workersPool
  }

  private def startSimulation(workers: List[ActorRef]): Array[Array[ActorRef]] = {

    val actors = ofDim[ActorRef](rows, cols)
    val horizontalBoundaryActors = ofDim[ActorRef](rows)
    val verticalBoundaryActors = ofDim[ActorRef](cols)
    val horizontalRoadDefs = ofDim[RoadDefinition](horizontalRoadsNum)
    val verticalRoadDefs = ofDim[RoadDefinition](verticalRoadsNum)

    val workersPool = buildWorkersPool(workers)

    // Create area actors
    for (i <- 0 until rows) {
      for (j <- 0 until cols) {
        actors(i)(j) = context.actorOf(AreaActor.props(config)
          .withDeploy(Deploy(scope = RemoteScope(workersPool.dequeue()))),
        s"AreaActor_${i}_$j")
      }
    }
    // Create horizontal boundary actors
    for (i <- 0 until rows) {
      if (i % 2 == 0) {
        horizontalBoundaryActors(i) = context.actorOf(
          BoundaryAreaActor.props(actors(i)(0), actors(i)(cols - 1))
            .withDeploy(Deploy(scope = RemoteScope(workersPool.dequeue()))),
          s"BoundaryActor_horizontal_$i"
        )
      } else {
        horizontalBoundaryActors(i) = context.actorOf(
          BoundaryAreaActor.props(actors(i)(cols - 1), actors(i)(0))
            .withDeploy(Deploy(scope = RemoteScope(workersPool.dequeue()))),
          s"BoundaryActor_horizontal_$i"
        )
      }
    }
    // Create vertical boundary actors
    for (j <- 0 until cols) {
      if (j % 2 == 0) {
        verticalBoundaryActors(j) = context.actorOf(
          BoundaryAreaActor.props(actors(rows - 1)(j), actors(0)(j))
            .withDeploy(Deploy(scope = RemoteScope(workersPool.dequeue()))),
          s"BoundaryActor_vertical_$j"
        )
      } else {
        verticalBoundaryActors(j) = context.actorOf(
          BoundaryAreaActor.props(actors(0)(j), actors(rows - 1)(j))
            .withDeploy(Deploy(scope = RemoteScope(workersPool.dequeue()))),
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

        for (vertRoad <- (j * size) until (j * size + size)) {
          val prevAreaActor = if (i == 0) verticalBoundaryActors(j) else actors(i - 1)(j)
          val nextAreaActor = if (i == rows - 1) verticalBoundaryActors(j) else actors(i + 1)(j)

          val roadDefinition = verticalRoadDefs(vertRoad)
          roadDefinition.direction match {
            case RoadDirection.NS =>
              areaVerticalRoadDefs += roadDefinition.toAreaRoadDefinition(nextAreaActor, prevAreaActor)
            case RoadDirection.SN =>
              areaVerticalRoadDefs += roadDefinition.toAreaRoadDefinition(prevAreaActor, nextAreaActor)
            case _ =>
              throw new IllegalStateException("Illegal direction of vertical road: " + roadDefinition.direction)
          }
        }

        for (horRoad <- (i * size) until (i * size + size)) {
          val prevAreaActor = if (j == 0) horizontalBoundaryActors(i) else actors(i)(j - 1)
          val nextAreaActor = if (j == cols - 1) horizontalBoundaryActors(i) else actors(i)(j + 1)

          val roadDefinition = horizontalRoadDefs(horRoad)
          roadDefinition.direction match {
            case RoadDirection.EW =>
              areaHorizontalRoadDefs += roadDefinition.toAreaRoadDefinition(prevAreaActor, nextAreaActor)
            case RoadDirection.WE =>
              areaHorizontalRoadDefs += roadDefinition.toAreaRoadDefinition(nextAreaActor, prevAreaActor)
            case _ =>
              throw new IllegalStateException("Illegal direction of horizontal road: " + roadDefinition.direction)
          }
        }

        actors(i)(j) ! StartSimulation(areaVerticalRoadDefs.toList, areaHorizontalRoadDefs.toList, i, j)
      }
    }
    actors
  }

}

object Supervisor {

  case object Start

  case object RegisterWorker

  case class StartVisualization(x: Int, y: Int)

  case class StopVisualization(x: Int, y: Int)

  case object CityVisualizationStartRequest

  case object CityVisualizationStopRequest

  case class TimeFrameUpdate(x: Int, y: Int, newTimeFrame: Long)

  def props(config: Config): Props = Props(classOf[Supervisor], config)

  // State
  sealed trait State

  case object Initial extends State

  case object Started extends State

  case object WaitingForWorkers extends State

  case object Working extends State

  case object TerminatingWorkers extends State


  // Data
  sealed trait Data

  case object EmptyData extends Data

  case class WorkersData(workers: List[ActorRef]) extends Data

  case class SupervisorData(workers: List[ActorRef],
                            areaActors: Array[Array[ActorRef]],
                            cityVisualizer: Option[ActorRef]
                           ) extends Data

}

case class RoadDefinition(roadId: RoadId, direction: RoadDirection) {
  def toAreaRoadDefinition(outgoingActorRef: ActorRef, prevAreaActorRef: ActorRef): AreaRoadDefinition =
    AreaRoadDefinition(roadId, direction, outgoingActorRef, prevAreaActorRef)
}
