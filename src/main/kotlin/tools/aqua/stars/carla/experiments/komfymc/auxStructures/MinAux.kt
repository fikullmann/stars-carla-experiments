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
import tools.aqua.stars.carla.experiments.komfymc.auxStructures.FutureAux
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

class MinAux<U : TickUnit<U, D>, D : TickDifference<D>>(
    endTS: TS<U, D>? = null,
    private var sAlphas: MutableList<Pair<TS<U, D>, SatProof>> = mutableListOf(),
    private var vAlphas: MutableList<Pair<TS<U, D>, ViolationProof>> = mutableListOf(),
) : FutureAux<U, D>(endTS) {
  fun copy() =
      MinAux(endTS,
              sAlphas.map { it.copy() }.toMutableList(),
              vAlphas.map { it.copy() }.toMutableList(),
              )
          .also {
            it.tsTpOut = tsTpOut.toMutableMap()
            it.tsTpIn = tsTpIn.toMutableMap()
            optimalProofs.map { it.copy() }.toMutableList()
          }
  /** updates the Eaux structure at arrival of a new TS/TP */
  fun updateMinAux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): MutableList<Proof> {
    shiftMinAux(interval, nts, ntp, factor)
    addTsTpFuture(interval, nts, ntp)
    addSubps(interval, nts, ntp, p1)

    shiftMinAux(interval, nts, ntp, factor, endTS)
    return findOptimalProofs(interval, nts)
  }

  /** updates the Eaux structure at arrival of a new TS/TP */
  fun update(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): Pair<MutableList<Proof>, MinAux<U, D>> {
    val copy = copy()
    val result = copy.updateMinAux(interval, nts, ntp, factor, p1)
    return result to copy
  }

  private fun addSubps(interval: RelativeInterval<D>, nts: TS<U, D>, ntp: TP, proof: Proof) {
    val firstTS = firstTsTp()?.second ?: throw NoExistingTsTp()
    when (proof) {
      is SatProof -> {
        if (nts >= firstTS + interval.startVal) {
          sAlphas.add(nts to proof)
          sAlphas.sortBy { it.second.size() }
        }
      }
      is ViolationProof -> {
        if (nts >= (interval.startVal?.let { firstTS + it } ?: firstTS)) vAlphas.add(nts to proof)
      }
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftMinAux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      factor: Double,
      end: TS<U, D>? = null
  ) {
    val tsTps = readyTsTps(interval, nts, end)
    tsTps.forEach { (tp, ts) ->
      if (vAlphas.isEmpty()) {
        optimalProofs.add(ts to SatMinAllSat(tp))
      }
      val correct = sAlphas.size.toDouble() / (sAlphas.size + vAlphas.size)
      if (correct >= factor) {
        val ltp = sAlphas.lastOrNull()?.second?.at() ?: ltp(tp)
        optimalProofs.add(
            ts to SatMinPrev(tp, ltp, correct, sAlphas.map { it.second }.toMutableList()))
      } else {
        optimalProofs.add(
            ts to
                VMinPrev(
                    tp,
                    vAlphas.last().second.at(),
                    correct,
                    vAlphas.map { it.second }.toMutableList()))
      }
      adjustMinAux(interval, nts, ntp)
    }
  }

  private fun adjustMinAux(interval: RelativeInterval<D>, nts: TS<U, D>, ntp: TP) {
    dropFirstTsTp()
    val (firstTp, firstTS) = firstTsTp() ?: (ntp to nts)

    sAlphas.removeIf { (ts, p) -> ts < (firstTS + interval.startVal) || p.at() < firstTp }
    vAlphas.removeIf { (ts, p) -> ts < (firstTS + interval.startVal) || p.at() < firstTp }
    shiftTsTpFuture(interval, firstTS, ntp)
  }
}
