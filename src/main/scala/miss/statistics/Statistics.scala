package miss.statistics

sealed trait Statistics[V] {
  def calculate(data: List[Double]): V
}

sealed trait CombinedStatistics[V] {
  def calculate(partialStatistics: List[Double], dataLengths: List[Int]): V
}

object MinStatistics extends Statistics[Double] {
  override def calculate(data: List[Double]): Double = {
    data.min
  }
}

object MaxStatistics extends Statistics[Double] {
  override def calculate(data: List[Double]): Double = {
    data.max
  }
}

object MeanStatistics extends Statistics[Double] {
  override def calculate(data: List[Double]): Double = {
    if (data.isEmpty) {
      0.0d
    } else {
      data.sum / data.length.toDouble
    }
  }
}


object VarianceStatistics extends Statistics[Double] {
  override def calculate(data: List[Double]): Double = {
    if (data.isEmpty) {
      0.0d
    } else {
      val mean = MeanStatistics.calculate(data)
      val sum = data.foldLeft(0.0d) { case (total, item) =>
        total + math.pow(item - mean, 2)
      }
      sum / data.length.toDouble
    }
  }
}

object StdDevStatistics extends Statistics[Double] {
  override def calculate(data: List[Double]): Double = {
    math.sqrt(VarianceStatistics.calculate(data))
  }
}

abstract class CompoundStatistics extends Statistics[List[Double]] {
  def listOfStatistics: List[Statistics[Double]]

  override def calculate(data: List[Double]): List[Double] = {
    listOfStatistics map {
      _.calculate(data)
    }
  }
}

object MinMaxMeanVarianceStdDevStatistics extends CompoundStatistics {
  override def listOfStatistics = List(
    MinStatistics,
    MaxStatistics,
    MeanStatistics,
    VarianceStatistics,
    StdDevStatistics
  )

  def calculateToString(data: List[Double]): String = {
    val min :: max :: mean :: variance :: stdDev :: Nil = calculate(data)
    s"Min: $min, Max: $max, Mean: $mean, Variance: $variance, Std Dev: $stdDev"
  }
}

object CombinedMinMaxMeanVarianceStdDevStatistics {
  def calculate(partialStatistics: List[List[Double]], dataLengths: List[Int]): List[Double] = {
    val n = dataLengths.sum.toDouble

    val min = partialStatistics.map(_(0)).min
    val max = partialStatistics.map(_(1)).max

    val means = partialStatistics.map(_(2))
    val meanSum = means.zip(dataLengths).foldLeft(0.0d) {
      case (total, (meanI, len)) => total + len * meanI
    }
    val mean = meanSum / n

    val variances = partialStatistics.map(_(3))
    val variancesSum = variances.zip(means).zip(dataLengths).foldLeft(0.0d) {
      case (total, ((varianceI, meanI), len)) => total + len * (varianceI + meanI * meanI)
    }
    val variance = variancesSum / n - mean * mean

    val stdDev = math.sqrt(variance)

    min :: max :: mean :: variance :: stdDev :: Nil
  }

  def calculateToString(partialStatistics: List[List[Double]], dataLengths: List[Int]): String = {
    val min :: max :: mean :: variance :: stdDev :: Nil = calculate(partialStatistics, dataLengths)
    s"Min: $min, Max: $max, Mean: $mean, Variance: $variance, Std Dev: $stdDev"
  }
}
