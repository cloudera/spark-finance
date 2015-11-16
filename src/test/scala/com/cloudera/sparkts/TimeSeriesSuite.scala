/**
 * Copyright (c) 2015, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package com.cloudera.sparkts

import breeze.linalg.DenseMatrix
import com.cloudera.sparkts.DateTimeIndex._
import com.cloudera.sparkts.TimeSeries._

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone._

import org.scalatest.{FunSuite, ShouldMatchers}

class TimeSeriesSuite extends FunSuite with ShouldMatchers {
  test("timeSeriesFromIrregularSamples") {
    val dt = new DateTime("2015-4-8")
    val samples = Array(
      ((dt, Array(1.0, 2.0, 3.0))),
      ((dt + 1.days, Array(4.0, 5.0, 6.0))),
      ((dt + 2.days, Array(7.0, 8.0, 9.0))),
      ((dt + 4.days, Array(10.0, 11.0, 12.0)))
    )

    val labels = Array("a", "b", "c", "d")
    val ts = timeSeriesFromIrregularSamples(samples, labels)
    ts.data.valuesIterator.toArray should be ((1 to 12).map(_.toDouble).toArray)
  }

  test("lagsIncludingOriginals") {
    val originalIndex = new UniformDateTimeIndex(0, 5, new DayFrequency(1))

    val data = DenseMatrix((1.0, 6.0), (2.0, 7.0), (3.0, 8.0), (4.0, 9.0), (5.0, 10.0))

    val originalTimeSeries = new TimeSeries(originalIndex, data, Array("a", "b"))

    val laggedTimeSeries = originalTimeSeries.lags(2, true, TimeSeries.laggedStringKey)

    laggedTimeSeries.keys should be (Array("a", "lag1(a)", "lag2(a)", "b", "lag1(b)", "lag2(b)"))
    laggedTimeSeries.index.size should be (3)
    laggedTimeSeries.data should be (DenseMatrix((3.0, 2.0, 1.0, 8.0, 7.0, 6.0),
      (4.0, 3.0, 2.0, 9.0, 8.0, 7.0), (5.0, 4.0, 3.0, 10.0, 9.0, 8.0)))
  }

  test("lagsExcludingOriginals") {
    val originalIndex = new UniformDateTimeIndex(0, 5, new DayFrequency(1))

    val data = DenseMatrix((1.0, 6.0), (2.0, 7.0), (3.0, 8.0), (4.0, 9.0), (5.0, 10.0))

    val originalTimeSeries = new TimeSeries(originalIndex, data, Array("a", "b"))

    val laggedTimeSeries = originalTimeSeries.lags(2, false)

    laggedTimeSeries.keys should be (Array(("a", 1), ("a", 2), ("b", 1), ("b", 2)))
    laggedTimeSeries.index.size should be (3)
    laggedTimeSeries.data should be (DenseMatrix((2.0, 1.0, 7.0, 6.0), (3.0, 2.0, 8.0, 7.0),
      (4.0, 3.0, 9.0, 8.0)))
  }

  test("customLags") {
    val originalIndex = new UniformDateTimeIndex(0, 5, new DayFrequency(1))

    val data = DenseMatrix((1.0, 6.0), (2.0, 7.0), (3.0, 8.0), (4.0, 9.0), (5.0, 10.0))

    val originalTimeSeries = new TimeSeries(originalIndex, data, Array("a", "b"))

    val lagMap = Map[String, (Boolean, Int)](("a" -> (true, 0)), ("b" -> (false, 2)))
    val laggedTimeSeries = originalTimeSeries.lagsPerColumn(lagMap,
      (key, lagOrder) => if (lagOrder == 0) { key } else { "lag" + lagOrder + "(" + key + ")" }
    )

    laggedTimeSeries.keys should be (Array("a", "lag1(b)", "lag2(b)"))
    laggedTimeSeries.index.size should be (3)
    laggedTimeSeries.data should be (DenseMatrix((3.0, 7.0, 6.0), (4.0, 8.0, 7.0), (5.0, 9.0, 8.0)))
  }

  test("timeDerivative") {
    val index = irregular(Array(
      "2015-04-14", "2015-04-15", "2015-04-17", "2015-04-22", "2015-04-26"
    ).map(new DateTime(_, UTC)))
    val data = DenseMatrix((1.0, 6.0), (2.0, 7.0), (3.0, 9.0), (8.0, 9.0), (5.0, 10.0))

    val originalTimeSeries = new TimeSeries(index, data, Array("a", "b"))

    val differencedTimeSeries = originalTimeSeries.timeDerivative(new DayFrequency(1))

    differencedTimeSeries.index.first.year().get() should be (2015)
    differencedTimeSeries.index.first.monthOfYear().get() should be (4)
    differencedTimeSeries.index.first.dayOfMonth().get() should be (15)
    differencedTimeSeries.index.last.year().get() should be (2015)
    differencedTimeSeries.index.last.monthOfYear().get() should be (4)
    differencedTimeSeries.index.last.dayOfMonth().get() should be (26)
    differencedTimeSeries.index.size should be (4)

    differencedTimeSeries.data should be (DenseMatrix(
      (1.0, 1.0),
      (0.5, 1.0),
      (1.0, 0.0),
      (-0.75, 0.25)
    ))
  }
}
