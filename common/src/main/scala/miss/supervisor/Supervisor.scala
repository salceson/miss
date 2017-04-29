package miss.supervisor

import akka.actor._
import akka.remote.RemoteScope
import com.typesafe.config.Config
import miss.cityvisualization.CityVisualizerActor
import miss.common.SerializableMessage
import miss.supervisor.Supervisor.{Data, State}
import miss.trafficsimulation.actors.AreaActor.EndWarmUpPhase
import miss.trafficsimulation.actors._
import miss.trafficsimulation.roads.RoadDirection.RoadDirection
import miss.trafficsimulation.roads._
import miss.worker.WorkerActor.{RegisterWorkerAck, Terminate}

import scala.Array.ofDim
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.language.postfixOps

class Supervisor(config: Config) extends FSM[State, Data] {

  import AreaActor.{EndSimulation, StartSimulation, VisualizationStartRequest, VisualizationStopRequest}
  import CityVisualizerActor._
  import Supervisor._

  private val cols = config.getInt("trafficsimulation.city.cols")
  private val rows = config.getInt("trafficsimulation.city.rows")
  private val size = config.getInt("trafficsimulation.area.size")
  private val verticalRoadsNum = cols * size
  private val horizontalRoadsNum = rows * size

  private val workerNodesCount = config.getInt("worker.nodes")
  private val workerCores = config.getInt("worker.areas_per_node")
  private val warmUpTimeSeconds = config.getInt("trafficsimulation.warmup.seconds")
  private val simulationTimeSeconds = config.getInt("trafficsimulation.time.seconds")

  startWith(Initial, EmptyData)

  when(Initial) {
    case Event(Start, EmptyData) =>
      log.info("Waiting for workers")
      goto(WaitingForWorkers) using WorkersData(List[ActorRef]())
  }

  when(WaitingForWorkers) {
    case Event(RegisterWorker, WorkersData(workers)) =>
      val senderActor = sender()
      senderActor ! RegisterWorkerAck

      log.info(senderActor.path.address.toString)

      val newWorkers = senderActor :: workers

      if (allWorkersRegistered(newWorkers)) {
        val areaActors = startSimulation(newWorkers)
        log.info("Starting warm up phase")
        setTimer("warm-up-timer", WarmUpDone, warmUpTimeSeconds seconds)
        goto(WarmUp) using SupervisorData(newWorkers, areaActors, None)
      }
      else {
        stay using WorkersData(newWorkers)
      }
  }

  when(WarmUp) {
    case Event(TimeFrameUpdate(x, y, newTimeFrame), SupervisorData(_, _, cityVisualizer)) =>
      log.debug(s"Got TimeFrameUpdate from actor $x, $y: frame $newTimeFrame")
      cityVisualizer.foreach(ar => ar ! CityVisualizationUpdate(x, y, newTimeFrame))
      stay
    case Event(WarmUpDone, SupervisorData(_, areaActors, _)) =>
      log.info("Starting simulation phase")
      for (i <- 0 until rows) {
        for (j <- 0 until cols) {
          val actor = areaActors(i)(j)
          actor ! EndWarmUpPhase
        }
      }
      setTimer("simulation-done-timer", SimulationDone, simulationTimeSeconds seconds)
      goto(Working)
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
      stay using SupervisorData(workers, areaActors, Some(sender()))
    case Event(CityVisualizationStopRequest, SupervisorData(workers, areaActors, _)) =>
      stay using SupervisorData(workers, areaActors, None)
    case Event(TimeFrameUpdate(x, y, newTimeFrame), SupervisorData(workers, areaActors, cityVisualizer)) =>
      log.debug(s"Got TimeFrameUpdate from actor $x, $y: frame $newTimeFrame")
      cityVisualizer.foreach(ar => ar ! CityVisualizationUpdate(x, y, newTimeFrame))
      stay using SupervisorData(workers, areaActors, cityVisualizer)
    case Event(SimulationDone, SupervisorData(workers, actors, cityVisualizer)) =>
      val actorsToTerminate = mutable.Set[ActorRef]()
      for (i <- 0 until rows) {
        for (j <- 0 until cols) {
          val actor = actors(i)(j)
          actor ! EndSimulation
          actorsToTerminate += actor
        }
      }
      goto(EndingSimulation) using TearDownData(workers, actorsToTerminate.toSet, Map[(Int, Int), Long]())
  }

  when(EndingSimulation) {
    case Event(SimulationResult(x, y, computedFrames), data@TearDownData(workers, actorsToTerminate, computedFramesByArea)) if actorsToTerminate.size > 1 =>
      stay using TearDownData(workers, actorsToTerminate - sender, computedFramesByArea + ((x, y) -> computedFrames))
    case Event(msg@SimulationResult(x, y, computedFrames), data@TearDownData(workers, actorsToTerminate, computedFramesByArea)) if actorsToTerminate.size == 1 =>
//      log.info(msg.toString)
      val computedFramesEntries = computedFramesByArea + ((x, y) -> computedFrames)

      val maxEntry = computedFramesEntries.maxBy(_._2)
      val minEntry = computedFramesEntries.minBy(_._2)

      val maxFps = maxEntry._2 / simulationTimeSeconds.toDouble
      val minFps = minEntry._2 / simulationTimeSeconds.toDouble
      val avgFps = computedFramesEntries.values.sum / computedFramesEntries.values.size.toDouble / simulationTimeSeconds.toDouble

      log.debug(s"Max result: $maxEntry")
      log.debug(s"Min result: $minEntry")

      val computedFramesMatrixTransposed = ofDim[Long](cols, rows)
      computedFramesEntries.foreach(e => computedFramesMatrixTransposed(e._1._2)(e._1._1) = e._2)
      log.info(s"Results matrix:\n${computedFramesMatrixTransposed.map{_.mkString(",")}.mkString("\n")}")

      log.info(s"Simulation done. Computed frames: ${minEntry._2}, min FPS: $minFps, max FPS: $maxFps, avg FPS: $avgFps")
      workers.foreach(worker => worker ! Terminate)
      goto(TerminatingWorkers) using data.copy(actorsToTerminate = Set())
  }

  when(TerminatingWorkers) {
    case Event(UnregisterWorker, data@TearDownData(workers, _, _)) =>
      val senderActor = sender()
      log.debug("Removing " + senderActor.toString())
      if (workers.size > 1) {
        stay using data.copy(workers = workers.filter(_ != senderActor))
      }
      else {
        context.system.terminate
        stop
      }
    case Event(_, _) => stay
  }

  private def allWorkersRegistered(workers: List[ActorRef]): Boolean = {
    workers.size == workerNodesCount
  }

  private def buildWorkersPool(workers: List[ActorRef]): mutable.Queue[Address] = {
    val list = for {
      worker <- workers
      core <- 0 until workerCores
    } yield (worker.path.address, core)

    val addresses = list map {
      _._1
    }
    mutable.Queue[Address](addresses: _*)
  }

  private def startSimulation(workers: List[ActorRef]): Array[Array[ActorRef]] = {

    val actors = ofDim[ActorRef](rows, cols)
    val horizontalRoadDefs = ofDim[RoadDefinition](horizontalRoadsNum)
    val verticalRoadDefs = ofDim[RoadDefinition](verticalRoadsNum)

    val workersPool = buildWorkersPool(workers)

    // Create area actors
    for (i <- 0 until rows) {
      for (j <- 0 until cols) {
        val worker = workersPool.dequeue()
        log.debug(s"starting actor ${i}_$j at ${worker.host.get}:${worker.port.get}")
        actors(i)(j) = context.actorOf(AreaActor.props(config)
          .withDeploy(Deploy(scope = RemoteScope(worker))),
          s"AreaActor_${i}_$j")
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
          val prevAreaActor = if (i == 0) actors(rows - 1)(j) else actors(i - 1)(j)
          val nextAreaActor = if (i == rows - 1) actors(0)(j) else actors(i + 1)(j)

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
          val prevAreaActor = if (j == 0) actors(i)(cols - 1) else actors(i)(j - 1)
          val nextAreaActor = if (j == cols - 1) actors(i)(0) else actors(i)(j + 1)

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

  // messages

  case object Start

  case object RegisterWorker extends SerializableMessage

  case object UnregisterWorker extends SerializableMessage

  case class StartVisualization(x: Int, y: Int)

  case class StopVisualization(x: Int, y: Int)

  case object CityVisualizationStartRequest

  case object CityVisualizationStopRequest

  case class TimeFrameUpdate(x: Int, y: Int, newTimeFrame: Long)

  case class SimulationResult(x: Int, y: Int, computedFrames: Long) extends SerializableMessage

  case object WarmUpDone

  case object SimulationDone

  def props(config: Config): Props = Props(classOf[Supervisor], config)

  // State
  sealed trait State

  case object Initial extends State

  case object Started extends State

  case object WaitingForWorkers extends State

  case object WarmUp extends State

  case object Working extends State

  case object EndingSimulation extends State

  case object TerminatingWorkers extends State


  // Data
  sealed trait Data

  case object EmptyData extends Data

  case class WorkersData(workers: List[ActorRef]) extends Data

  case class SupervisorData(workers: List[ActorRef],
                            areaActors: Array[Array[ActorRef]],
                            cityVisualizer: Option[ActorRef]
                           ) extends Data

  case class TearDownData(workers: List[ActorRef],
                          actorsToTerminate: Set[ActorRef],
                          computedFramesByArea: Map[(Int, Int), Long]
                         ) extends Data

}

case class RoadDefinition(roadId: RoadId, direction: RoadDirection) {
  def toAreaRoadDefinition(outgoingActorRef: ActorRef, prevAreaActorRef: ActorRef): AreaRoadDefinition =
    AreaRoadDefinition(roadId, direction, outgoingActorRef, prevAreaActorRef)
}
