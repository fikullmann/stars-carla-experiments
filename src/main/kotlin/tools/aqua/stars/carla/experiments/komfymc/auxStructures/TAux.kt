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

package tools.aqua.auxStructures

import kotlin.math.abs
import tools.aqua.stars.carla.experiments.komfymc.BoundedInterval
import tools.aqua.stars.carla.experiments.komfymc.TP
import tools.aqua.stars.carla.experiments.komfymc.TS

abstract open class TAux(
    var tsTpIn: MutableMap<TP, TS> = mutableMapOf(),
    var tsTpOut: MutableMap<TP, TS> = mutableMapOf(),
) {
  fun firstTsTp() =
      tsTpOut.entries.firstOrNull()?.toPair() ?: tsTpIn.entries.firstOrNull()?.toPair()

  fun dropFirstTsTp() {
    if (tsTpOut.isNotEmpty()) {
      tsTpOut.remove(tsTpOut.keys.first())
    } else tsTpIn.remove(tsTpIn.keys.first())
  }
  /** updates tsTpIn and Out so the correct pairs are in In and Out depending on current TSTP */
  fun shiftTsTpsPast(interval: BoundedInterval, iStart: Double, ts: TS, tp: TP) {
    // top branch is not actually needed..(?)
    if (abs(iStart) < 0.0001) {
      tsTpIn[tp] = ts
      tsTpIn.entries.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    } else {
      tsTpOut[tp] = ts
      tsTpOut.forEach { (tsL, tpL) ->
        if (tsL.i <= interval.endVal) {
          tsTpIn[tsL] = tpL
        }
      }
      tsTpOut.entries.removeIf { (tsL, _) -> tsL.i <= interval.endVal }
      tsTpIn.entries.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    }
  }

  fun shiftTsTpFuture(interval: BoundedInterval, firstTs: TS, tpCur: TP) {
    tsTpIn.forEach { (tp, ts) ->
      if (ts.i < firstTs.i + interval.startVal && tp < tpCur) {
        tsTpOut[tp] = ts
      }
    }
    tsTpOut.entries.removeIf { (tp, ts) -> ts < firstTs && tp < tpCur }
    tsTpIn.entries.removeIf { (tp, ts) -> ts.i < firstTs.i + interval.startVal && tp < tpCur }
  }

  fun addTsTpFuture(interval: BoundedInterval, nts: TS, ntp: TP) {
    if (tsTpOut.isNotEmpty() || tsTpIn.isNotEmpty()) {
      val firstTs = firstTsTp()?.second ?: throw UnboundedFuture()
      if (nts < firstTs + interval.startVal) {
        tsTpOut[ntp] = nts
      } else {
        tsTpIn[ntp] = nts
      }
    } else {
      if (abs(interval.startVal) < 0.0001) {
        tsTpIn[ntp] = nts
      } else {
        tsTpOut[ntp] = nts
      }
    }
  }

  fun readyTsTps(interval: BoundedInterval, nts: TS): MutableList<Pair<TS, TP>> =
      buildList {
            tsTpIn.forEach { (tp, ts) -> if (ts + interval.endVal < nts) add(ts to tp) }
            tsTpOut.forEach { (tp, ts) -> if (ts + interval.endVal < nts) add(ts to tp) }
          }
          .toMutableList()

  fun readyTsTps(interval: BoundedInterval, nts: TS, endTS: Double?): Map<TP, TS> =
      if (endTS == nts.i) {
        (tsTpOut + tsTpIn)
      } else
          (tsTpIn.filter { (_, v) -> v + interval.endVal < nts } +
              tsTpOut.filter { (_, v) -> v + interval.endVal < nts })

  fun tsTpOf(stp: TP): TS = tsTpOut[stp] ?: tsTpIn[stp] ?: throw NoExistingTsTp()

  fun etp(tp: TP): TP {
    return when (tsTpIn.entries.firstOrNull()) {
      null -> tsTpOut.entries.firstOrNull()?.key ?: tp
      else -> tsTpIn.entries.first().key
    }
  }

  fun ltp(tp: TP): TP = tsTpOut.entries.lastOrNull()?.key ?: tp
}
