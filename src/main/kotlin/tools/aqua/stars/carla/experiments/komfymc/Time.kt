/*
 * Copyright 2024 The STARS Carla Experiments Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.stars.carla.experiments.komfymc

/** Timestamp */
@JvmInline
value class TS(val i: Double) : Comparable<TS> {
  override fun compareTo(other: TS) = i.compareTo(other.i)

  operator fun plus(other: Double) = TS(i + other)

  operator fun minus(other: Double) = TS(i - other)

  operator fun minus(other: TS) = TS(i - other.i)
}

/** Timepoint */
@JvmInline
value class TP(val i: Int) : Comparable<TP> {
  override fun compareTo(other: TP): Int {
    return i - other.i
  }

  operator fun plus(other: Int) = TP(i + other)

  operator fun minus(other: Int) = TP(i - other)
}

abstract class Interval(open val startVal: Double = 0.0) {
  /** returns true when t is inside the Interval */
  abstract fun contains(t: Double): Boolean

  /** returns true when Timepoint t is before the Interval */
  abstract fun below(t: Double): Boolean

  /** returns true when Timepoint t is after the Interval */
  abstract fun above(t: Double): Boolean
}

data class InfInterval(override val startVal: Double = 0.0) : Interval() {
  override fun contains(t: Double) = (t >= startVal)

  override fun below(t: Double) = t < startVal

  override fun above(t: Double) = false
}

data class BoundedInterval(override val startVal: Double = 0.0, val endVal: Double = 0.0) :
    Interval() {
  override fun contains(t: Double) = (t in startVal..endVal)

  override fun below(t: Double) = t < startVal

  override fun above(t: Double) = t > endVal
}
