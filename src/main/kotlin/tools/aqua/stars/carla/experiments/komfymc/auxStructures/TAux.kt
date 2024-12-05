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

package tools.aqua.stars.carla.experiments.komfymc.auxStructures

import tools.aqua.*
import tools.aqua.auxStructures.NoExistingTsTp
import tools.aqua.auxStructures.UnboundedFuture
import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

abstract open class TAux<U : TickUnit<U, D>, D : TickDifference<D>>(
    var tsTpIn: MutableMap<TP, TS<U, D>> = mutableMapOf(),
    var tsTpOut: MutableMap<TP, TS<U, D>> = mutableMapOf(),
) {
  fun firstTsTp() =
      tsTpOut.entries.firstOrNull()?.toPair() ?: tsTpIn.entries.firstOrNull()?.toPair()

  fun dropFirstTsTp() {
    if (tsTpOut.isNotEmpty()) {
      tsTpOut.remove(tsTpOut.keys.first())
    } else tsTpIn.remove(tsTpIn.keys.first())
  }
  /** updates tsTpIn and Out so the correct pairs are in In and Out depending on current TSTP */
  fun shiftTsTpsPast(interval: RealInterval<U, D>, iStart: D?, ts: TS<U, D>, tp: TP) {
    // top branch is not actually needed..(?)
    if (iStart == null) { // TODO: detect iStart == 0
      tsTpIn[tp] = ts
      tsTpIn.entries.removeIf { (_, tsL) -> tsL < interval.startVal }
    } else {
      tsTpOut[tp] = ts
      tsTpOut.forEach { (tpL, tsL) ->
        if (tsL <= interval.endVal) {
          tsTpIn[tpL] = tsL
        }
      }
      tsTpOut.entries.removeIf { (_, tsL) -> tsL <= interval.endVal }
      tsTpIn.entries.removeIf { (_, tsL) -> tsL < interval.startVal }
    }
  }

  fun tsTpOf(stp: TP): TS<U, D> = tsTpOut[stp] ?: tsTpIn[stp] ?: throw NoExistingTsTp()

  fun etp(tp: TP): TP {
    return when (tsTpIn.entries.firstOrNull()) {
      null -> tsTpOut.entries.firstOrNull()?.key ?: tp
      else -> tsTpIn.entries.first().key
    }
  }

  fun ltp(tp: TP): TP = tsTpOut.entries.lastOrNull()?.key ?: tp
}

abstract open class FutureAux<U : TickUnit<U, D>, D : TickDifference<D>>(
  val endTS: TS<U, D>?,
    var tsTpIn: MutableMap<TP, TS<U, D>> = mutableMapOf(),
    var tsTpOut: MutableMap<TP, TS<U, D>> = mutableMapOf(),
    var optimalProofs: MutableList<Pair<TS<U, D>, Proof>> = mutableListOf(),
) {
  fun firstTsTp() =
      tsTpOut.entries.firstOrNull()?.toPair() ?: tsTpIn.entries.firstOrNull()?.toPair()

  fun dropFirstTsTp() {
    if (tsTpOut.isNotEmpty()) {
      tsTpOut.remove(tsTpOut.keys.first())
    } else tsTpIn.remove(tsTpIn.keys.first())
  }

  fun findOptimalProofs(interval: RelativeInterval<D>, nts: TS<U, D>): MutableList<Proof> {
    if (endTS == nts) {
      return optimalProofs.map { it.second }.toMutableList()
    } else {
      if (interval.endVal != null) {
        val result = optimalProofs.filter { (ts, _) -> (ts + interval.endVal < nts) }
        optimalProofs.removeIf { (ts, _) -> (ts + interval.endVal < nts) }
        return result.map { it.second }.toMutableList()
      } else return mutableListOf()
    }
  }

  fun shiftTsTpFuture(interval: RelativeInterval<D>, firstTs: TS<U, D>, tpCur: TP) {
    tsTpIn.forEach { (tp, ts) ->
      if (ts < firstTs + interval.startVal && tp < tpCur) {
        tsTpOut[tp] = ts
      }
    }
    tsTpOut.entries.removeIf { (tp, ts) -> ts < firstTs && tp < tpCur }
    tsTpIn.entries.removeIf { (tp, ts) -> ts < firstTs + interval.startVal && tp < tpCur }
    if (tsTpIn.size == 1 && tsTpOut.isEmpty() && interval.startVal != null) {
      tsTpOut.putAll(tsTpIn)
      tsTpIn.clear()
    }
  }

  fun addTsTpFuture(interval: RelativeInterval<D>, nts: TS<U, D>, ntp: TP) {
    if (tsTpOut.isNotEmpty() || tsTpIn.isNotEmpty()) {
      val firstTS = firstTsTp()?.second ?: throw UnboundedFuture()
      if (nts < firstTS + interval.startVal) {
        tsTpOut[ntp] = nts
      } else {
        tsTpIn[ntp] = nts
      }
    } else {
      if (interval.startVal == null) {
        tsTpIn[ntp] = nts
      } else {
        tsTpOut[ntp] = nts
      }
    }
  }

  fun readyTsTps(interval: RelativeInterval<D>, nts: TS<U, D>): MutableList<Pair<TS<U, D>, TP>> =
      buildList {
            tsTpIn.forEach { (tp, ts) -> if (ts + interval.endVal < nts) add(ts to tp) }
            tsTpOut.forEach { (tp, ts) -> if (ts + interval.endVal < nts) add(ts to tp) }
          }
          .toMutableList()

  fun readyTsTps(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      endTS: TS<U, D>?
  ): Map<TP, TS<U, D>> =
      if (endTS == nts) (tsTpOut + tsTpIn)
      else {
        if (interval.endVal == null) { mutableMapOf<TP, TS<U, D>>() } else {
          tsTpOut.filter { (_, ts) -> ts + interval.endVal < nts } +
                  (tsTpIn.filter { (_, ts) -> ts + interval.endVal < nts })
        }
      }

  fun tsTpOf(stp: TP): TS<U, D> = tsTpOut[stp] ?: tsTpIn[stp] ?: throw NoExistingTsTp()

  fun ltp(tp: TP): TP = tsTpOut.entries.lastOrNull()?.key ?: tp
}
