package miss.statistics

import org.specs2.mutable.Specification

class StatisticsSpec extends Specification {
  val data = List(0d, 1d, 2d, 3d, 4d)

  val data1 = List(0d, 1d, 2d, 3d, 4d)
  val data2 = List(2d, 1d, 2d, 2d)

  "MinStatistics" should {
    "properly calculate minimum" in {
      MinStatistics.calculate(data) mustEqual 0d
    }
  }

  "MaxStatistics" should {
    "properly calculate maximum" in {
      MaxStatistics.calculate(data) mustEqual 4d
    }
  }

  "MeanStatistics" should {
    "properly calculate mean" in {
      MeanStatistics.calculate(data) mustEqual 2d
    }
  }

  "VarianceStatistics" should {
    "properly calculate variance" in {
      VarianceStatistics.calculate(data) mustEqual 2d
    }
  }

  "StdDevStatistics" should {
    "properly calculate std dev" in {
      StdDevStatistics.calculate(data) mustEqual math.sqrt(2d)
    }
  }

  "MinMaxMeanVarianceStdDevStatistics" should {
    "properly calculate statistics" in {
      val expected = 0d :: 4d :: 2d :: 2d :: math.sqrt(2d) :: Nil
      MinMaxMeanVarianceStdDevStatistics.calculate(data) mustEqual expected
    }
  }

  "CombinedMinMaxMeanVarianceStdDevStatistics" should {
    "properly calculate statistics" in {
      val min1 = MinStatistics.calculate(data1)
      val max1 = MaxStatistics.calculate(data1)
      val mean1 = MeanStatistics.calculate(data1)
      val var1 = VarianceStatistics.calculate(data1)
      val std1 = StdDevStatistics.calculate(data1)

      val min2 = MinStatistics.calculate(data2)
      val max2 = MaxStatistics.calculate(data2)
      val mean2 = MeanStatistics.calculate(data2)
      val var2 = VarianceStatistics.calculate(data2)
      val std2 = StdDevStatistics.calculate(data2)

      val stats1 = min1 :: max1 :: mean1 :: var1 :: std1 :: Nil
      val stats2 = min2 :: max2 :: mean2 :: var2 :: std2 :: Nil

      val combinedData = data1 ++ data2

      val minAggExpected = MinStatistics.calculate(combinedData)
      val maxAggExpected = MaxStatistics.calculate(combinedData)
      val meanAggExpected = MeanStatistics.calculate(combinedData)
      val varAggExpected = VarianceStatistics.calculate(combinedData)
      val stdAggExpected = StdDevStatistics.calculate(combinedData)

      val statsExpected = minAggExpected :: maxAggExpected :: meanAggExpected :: varAggExpected :: stdAggExpected :: Nil

      val stats = CombinedMinMaxMeanVarianceStdDevStatistics
        .calculate(List(stats1, stats2), List(data1.length, data2.length))

      stats zip statsExpected map {
        case (stat, expected) => math.abs(stat - expected) must be lessThan 1e-13
      }
    }
  }

}
