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

import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.carla.experiments.komfymc.auxStructures.TAux
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

class PastMinAux<U : TickUnit<U, D>, D : TickDifference<D>>(
    private var tsZero: TS<U, D>? = null,
    val sAlphasIn: MutableList<Pair<TS<U, D>, SatProof>> = mutableListOf(),
    val sAlphasOut: MutableList<Pair<TS<U, D>, SatProof>> = mutableListOf(),
    val vAlphasIn: MutableList<Pair<TS<U, D>, ViolationProof>> = mutableListOf(),
    val vAlphasOut: MutableList<Pair<TS<U, D>, ViolationProof>> = mutableListOf()
) : TAux<U, D>() {

  fun updatePMinaux(
      interval: RelativeInterval<D>,
      ts: TS<U, D>,
      tp: TP,
      factor: Double,
      p: Proof
  ): Proof {
    val currTsZero = tsZero ?: ts
    tsZero = currTsZero
    addSubps(ts, p)
    var l = currTsZero
    if (ts < currTsZero + interval.startVal) {
      l = (ts - interval.endVal)
    }
    val r = interval.startVal?.let { ts - it } ?: ts
    shiftPMinaux(RealInterval(l, r), interval.startVal, ts, tp)
    return evalPMinaux(tp, factor)
  }

  private fun addSubps(ts: TS<U, D>, p: Proof) {
    when (p) {
      is SatProof -> sAlphasOut.add(ts to p)
      is ViolationProof -> vAlphasOut.add(ts to p)
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftPMinaux(interval: RealInterval<U, D>, iStart: D?, ts: TS<U, D>, tp: TP) {
    shiftTsTpsPast(interval, iStart, ts, tp)

    val newInSat = sAlphasOut.filter { (ts, _) -> interval.contains(ts) }
    sAlphasOut.removeIf { (ts, _) -> ts <= interval.endVal }
    sAlphasOut.addAll(newInSat)

    val newInVio = vAlphasOut.filter { (ts, _) -> interval.contains(ts) }
    vAlphasOut.removeIf { (ts, _) -> ts <= interval.endVal }
    if (newInVio.isNotEmpty()) {
      vAlphasOut.addAll(newInVio)
      vAlphasOut.sortBy { it.second.size() }
    }

    sAlphasIn.removeIf { (tsL, _) -> tsL < interval.startVal }
    sAlphasOut.removeIf { (tsL, _) -> tsL <= interval.endVal }
    vAlphasIn.removeIf { (tsL, _) -> tsL < interval.startVal }
    vAlphasOut.removeIf { (tsL, _) -> tsL <= interval.endVal }
  }

  private fun evalPMinaux(tp: TP, factor: Double): Proof {
    // if vAlphas is empty then correct is always = 1.0 -> always fulfilled
    if (vAlphasIn.isEmpty()) {
      return SatPastMinAllSat(tp)
    }
    val correct = sAlphasIn.size.toDouble() / (sAlphasIn.size + vAlphasIn.size)
    return if (correct >= factor) {
      val etp = sAlphasIn.firstOrNull()?.second?.at() ?: etp(tp)
      SatPastMinPrev(tp, etp, correct, sAlphasIn.map { it.second }.toMutableList())
    } else {
      VPastMinPrev(
          tp, vAlphasIn.first().second.at(), correct, vAlphasIn.map { it.second }.toMutableList())
    }
  }

  fun copy() =
      PastMinAux(
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
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): Pair<Proof, PastMinAux<U, D>> {
    val copy = copy()
    val result = copy.updatePMinaux(interval, nts, ntp, factor, p1)
    return result to copy
  }
}
