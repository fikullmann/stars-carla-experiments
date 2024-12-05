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

import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

/** Timestamp */
@JvmInline
value class TS<U : TickUnit<U, D>, D : TickDifference<D>>(val i: U) : Comparable<TS<U, D>> {
  override fun compareTo(other: TS<U, D>) = i.compareTo(other.i)

//  operator fun plus(other: D) = TS(i + other)

//  operator fun minus(other: D) = TS(i - other)

  operator fun plus(other: D?) = if (other != null) TS(i + other) else this

  operator fun minus(other: D?) = if (other != null) TS(i - other) else this

  // operator fun minus(other: TS<U, D>) = TS<U, D>(i - other.i)
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

class RelativeInterval<D : TickDifference<D>>(val startVal: D? = null, val endVal: D? = null) {
  /** returns true when t is inside the Interval */
  fun contains(t: D) =
      if (startVal != null) {
        if (endVal != null) t in startVal..endVal else t <= startVal
      } else {
        if (endVal != null) (t <= endVal) else true
      }

  /** returns true when Timepoint t is before the Interval */
  fun below(t: D) = startVal?.let { t < startVal } ?: false

  /** returns true when Timepoint t is after the Interval */
  fun above(t: D) = endVal?.let { t > endVal } ?: false
}

data class RealInterval<U : TickUnit<U, D>, D : TickDifference<D>>(
    val startVal: TS<U, D>,
    val endVal: TS<U, D>
) {
  fun contains(t: TS<U, D>) = t in startVal..endVal

  fun below(t: TS<U, D>) = t < startVal

  fun above(t: TS<U, D>) = t > endVal
}
