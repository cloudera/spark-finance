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

import com.github.nscala_time.time.Imports._

import DateTimeIndex._

/**
 * A DateTimeIndex maintains a bi-directional mapping between integers and an ordered collection of
 * date-times. Multiple date-times may correspond to the same integer, implying multiple samples
 * at the same date-time.
 *
 * To avoid confusion between the meaning of "index" as it appears in "DateTimeIndex" and "index"
 * as a location in an array, in the context of this class, we use "location", or "loc", to refer
 * to the latter.
 */
trait DateTimeIndex extends Serializable {
  /**
   * Returns a sub-slice of the index, starting and ending at the given date-times (inclusive).
   */
  def slice(start: DateTime, end: DateTime): DateTimeIndex

  /**
   * Returns a sub-slice of the index with the given range of indices.
   */
  def slice(range: Range): DateTimeIndex

  /**
   * Returns a sub-slice of the index, starting and ending at the given indices (inclusive).
   */
  def slice(start: Int, end: Int): DateTimeIndex

  /**
   * The first date-time in the index.
   */
  def first(): DateTime

  /**
   * The last date-time in the index. Inclusive.
   */
  def last(): DateTime

  /**
   * The number of date-times in the index.
   */
  def size(): Int

  /**
   * The i-th date-time in the index.
   */
  def dateTimeAtLoc(i: Int): DateTime

  /**
   * The location of the given date-time. If the index contains the date-time more than once,
   * returns its first appearance. If the given date-time does not appear in the index, returns -1.
   */
  def locAtDateTime(dt: DateTime): Int
}

/**
 * An implementation of DateTimeIndex that contains date-times spaced at regular intervals. Allows
 * for constant space storage and constant time operations.
 */
class UniformDateTimeIndex(val start: Long, val periods: Int, val frequency: Frequency)
  extends DateTimeIndex {

  /**
   * {@inheritDoc}
   */
  override def first(): DateTime = new DateTime(start)

  /**
   * {@inheritDoc}
   */
  override def last(): DateTime = frequency.advance(new DateTime(first), periods - 1)

  /**
   * {@inheritDoc}
   */
  override def size: Int = periods

  /**
   * {@inheritDoc}
   */
  override def slice(start: DateTime, end: DateTime): UniformDateTimeIndex = {
    uniform(start, frequency.difference(start, end) + 1, frequency)
  }

  /**
   * {@inheritDoc}
   */
  override def slice(range: Range): UniformDateTimeIndex = {
    slice(range.head, range.last)
  }

  /**
   * {@inheritDoc}
   */
  override def slice(lower: Int, upper: Int): UniformDateTimeIndex = {
    uniform(frequency.advance(new DateTime(first), lower), upper - lower + 1, frequency)
  }

  /**
   * {@inheritDoc}
   */
  override def dateTimeAtLoc(loc: Int): DateTime = frequency.advance(new DateTime(first), loc)

  /**
   * {@inheritDoc}
   */
  override def locAtDateTime(dt: DateTime): Int = {
    val loc = frequency.difference(new DateTime(first), dt)
    if (dateTimeAtLoc(loc) == dt) {
      loc
    } else {
      -1
    }
  }

  override def equals(other: Any): Boolean = {
    val otherIndex = other.asInstanceOf[UniformDateTimeIndex]
    otherIndex.first == first && otherIndex.periods == periods && otherIndex.frequency == frequency
  }

  override def toString(): String = {
    Array(
      "uniform", new DateTime(start).toString, periods.toString, frequency.toString).mkString(",")
  }
}

/**
 * An implementation of DateTimeIndex that allows date-times to be spaced at uneven intervals.
 * Lookups or slicing by date-time are O(log n) operations..
 */
class IrregularDateTimeIndex(val instants: Array[Long]) extends DateTimeIndex {
  /**
   * {@inheritDoc}
   */
  override def slice(start: DateTime, end: DateTime): IrregularDateTimeIndex = {
    throw new UnsupportedOperationException()
  }

  /**
   * {@inheritDoc}
   */
  override def slice(range: Range): IrregularDateTimeIndex = {
    throw new UnsupportedOperationException()
  }

  /**
   * {@inheritDoc}
   */
  override def slice(start: Int, end: Int): IrregularDateTimeIndex = {
    throw new UnsupportedOperationException()
  }

  /**
   * {@inheritDoc}
   */
  override def first(): DateTime = new DateTime(instants(0))

  /**
   * {@inheritDoc}
   */
  override def last(): DateTime = new DateTime(instants(instants.length - 1))

  /**
   * {@inheritDoc}
   */
  override def size(): Int = instants.length

  /**
   * {@inheritDoc}
   */
  override def dateTimeAtLoc(loc: Int): DateTime = new DateTime(instants(loc))

  /**
   * {@inheritDoc}
   */
  override def locAtDateTime(dt: DateTime): Int = {
    java.util.Arrays.binarySearch(instants, dt.getMillis)
  }

  override def equals(other: Any): Boolean = {
    val otherIndex = other.asInstanceOf[IrregularDateTimeIndex]
    otherIndex.instants.sameElements(instants)
  }

  override def toString(): String = {
    "irregular," + instants.map(new DateTime(_).toString).mkString(",")
  }
}

object DateTimeIndex {
  /**
   * Create a UniformDateTimeIndex with the given start time, number of periods, and frequency.
   */
  def uniform(start: DateTime, periods: Int, frequency: Frequency): UniformDateTimeIndex = {
    new UniformDateTimeIndex(start.getMillis, periods, frequency)
  }

  /**
   * Create a UniformDateTimeIndex with the given start time and end time (inclusive) and frequency.
   */
  def uniform(start: DateTime, end: DateTime, frequency: Frequency): UniformDateTimeIndex = {
    uniform(start, frequency.difference(start, end) + 1, frequency)
  }

  /**
   * Create an IrregularDateTimeIndex composed of the given date-times.
   */
  def irregular(dts: Array[DateTime]): IrregularDateTimeIndex = {
    new IrregularDateTimeIndex(dts.map(_.getMillis))
  }

  /**
   * Finds the next business day occurring at or after the given date-time.
   */
  def nextBusinessDay(dt: DateTime): DateTime = {
    if (dt.getDayOfWeek == 6) {
      dt + 2.days
    } else if (dt.getDayOfWeek == 7) {
      dt + 1.days
    } else {
      dt
    }
  }

  implicit def periodToFrequency(period: Period): Frequency = {
    if (period.getDays != 0) {
      return new DayFrequency(period.getDays)
    }
    throw new UnsupportedOperationException()
  }

  implicit def intToBusinessDayRichInt(n: Int): BusinessDayRichInt = new BusinessDayRichInt(n)

  /**
   * Parses a DateTimeIndex from the output of its toString method
   */
  def fromString(str: String): DateTimeIndex = {
    val tokens = str.split(",")
    tokens(0) match {
      case "uniform" => {
        val start = new DateTime(tokens(1))
        val periods = tokens(2).toInt
        val freqTokens = tokens(3).split(" ")
        val freq = freqTokens(0) match {
          case "days" => new DayFrequency(freqTokens(1).toInt)
          case "businessDays" => new BusinessDayFrequency(freqTokens(1).toInt)
          case _ => throw new IllegalArgumentException(s"Frequency ${freqTokens(0)} not recognized")
        }
        uniform(start, periods, freq)
      }
      case "irregular" => {
        val dts = new Array[DateTime](tokens.length - 1)
        for (i <- 1 until tokens.length) {
          dts(i - 1) = new DateTime(tokens(i))
        }
        irregular(dts)
      }
      case _ => throw new IllegalArgumentException(
        s"DateTimeIndex type ${tokens(0)} not recognized")
    }
  }
}