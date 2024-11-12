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

import kotlin.math.max
import tools.aqua.stars.carla.experiments.komfymc.*

class PastMaxAux(
    private var tsZero: TS? = null,
    val sAlphasIn: MutableList<Pair<TS, SatProof>> = mutableListOf(),
    val sAlphasOut: MutableList<Pair<TS, SatProof>> = mutableListOf(),
    val vAlphasIn: MutableList<Pair<TS, ViolationProof>> = mutableListOf(),
    val vAlphasOut: MutableList<Pair<TS, ViolationProof>> = mutableListOf()
) : TAux() {

  fun updatePMaxAux(interval: Interval, ts: TS, tp: TP, factor: Double, p: Proof): Proof {
    val currTsZero = tsZero ?: ts
    tsZero = currTsZero
    addSubps(ts, p)
    val l = if (interval is BoundedInterval) max(0.0, (ts.i - interval.endVal)) else currTsZero.i
    val r = ts.i - interval.startVal
    shiftPMaxAux(BoundedInterval(l, r), interval.startVal, ts, tp)
    return evalPMaxAux(tp, factor)
  }

  private fun addSubps(ts: TS, p: Proof) {
    when (p) {
      is SatProof -> sAlphasOut.add(ts to p)
      is ViolationProof -> vAlphasOut.add(ts to p)
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftPMaxAux(interval: BoundedInterval, iStart: Double, ts: TS, tp: TP) {
    shiftTsTpsPast(interval, iStart, ts, tp)

    val newInSat = sAlphasOut.filter { (ts, _) -> interval.contains(ts.i) }
    sAlphasOut.removeIf { (ts, _) -> ts.i <= interval.endVal }
    sAlphasIn.addAll(newInSat)

    val newInVio = vAlphasOut.filter { (ts, _) -> interval.contains(ts.i) }
    vAlphasOut.removeIf { (ts, _) -> ts.i <= interval.endVal }
    if (newInVio.isNotEmpty()) {
      vAlphasIn.addAll(newInVio)
      vAlphasIn.sortBy { it.second.size() }
    }

    sAlphasIn.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    sAlphasOut.removeIf { (tsL, _) -> tsL.i <= interval.endVal }
    vAlphasIn.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    vAlphasOut.removeIf { (tsL, _) -> tsL.i <= interval.endVal }
  }

  private fun evalPMaxAux(tp: TP, factor: Double): Proof {
    // if vAlphasIn is empty, correct equals 1.0 or is undefined, resulting in violation either way.
    if (vAlphasIn.isEmpty()) {
      return VPastMaxAllSat(tp)
    }
    val correct = sAlphasIn.size.toDouble() / (sAlphasIn.size + vAlphasIn.size)
    return if (correct < factor) {
      val etp = sAlphasIn.firstOrNull()?.second?.at() ?: etp(tp)
      SatPastMaxPrev(tp, etp, correct, sAlphasIn.map { it.second }.toMutableList())
    } else {
      VPastMaxPrev(
          tp, vAlphasIn.first().second.at(), correct, vAlphasIn.map { it.second }.toMutableList())
    }
  }

  fun copy() =
      PastMaxAux(
              tsZero,
              sAlphasIn.map { it.copy() }.toMutableList(),
              sAlphasOut.map { it.copy() }.toMutableList(),
              vAlphasIn.map { it.copy() }.toMutableList(),
              vAlphasOut.map { it.copy() }.toMutableList(),
          )
          .also {
            it.tsTpOut = tsTpOut.toMutableMap()
            it.tsTpIn = tsTpIn.toMutableMap()
          }

  fun update1(
      interval: Interval,
      nts: TS,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): Pair<Proof, PastMaxAux> {
    val copy = copy()
    val result = copy.updatePMaxAux(interval, nts, ntp, factor, p1)
    return result to copy
  }
}
