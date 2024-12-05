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

class Aaux<U : TickUnit<U, D>, D : TickDifference<D>>(
    endTS: TS<U, D>? = null,
    private var sAlphas: MutableList<Pair<TS<U, D>, SatProof>> = mutableListOf(),
    private var vAlphas: MutableList<Pair<TS<U, D>, Proof>> = mutableListOf(),
) : FutureAux<U, D>(endTS) {
  fun copy() =
      Aaux(endTS,
              sAlphas.map { it.copy() }.toMutableList(),
              vAlphas.map { it.copy() }.toMutableList())
          .also { aux ->
            aux.tsTpOut = tsTpOut.toMutableMap()
            aux.tsTpIn = tsTpIn.toMutableMap()
            aux.optimalProofs = aux.optimalProofs.map { it.copy() }.toMutableList()
          }
  /** updates the Eaux structure at arrival of a new TS/TP */
  fun updateAaux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      p1: Proof
  ): MutableList<Proof> {
    shiftAaux(interval, nts, ntp)
    addTsTpFuture(interval, nts, ntp)
    addSubps(interval, nts, ntp, p1)

    shiftAaux(interval, nts, ntp, endTS)
    return findOptimalProofs(interval, nts)
  }

  /** updates the Eaux structure at arrival of a new TS/TP */
  fun update(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      p1: Proof
  ): Pair<MutableList<Proof>, Aaux<U, D>> {
      if (ntp.i == 0) {
          val copy = copy()
          val result = copy.updateAaux(interval, nts, ntp, p1)
          return result to copy
      } else {
          return updateAaux(interval, nts, ntp, p1) to this
      }
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
        if (nts >= firstTS + interval.startVal) vAlphas.add(nts to proof)
      }
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftAaux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      end: TS<U, D>? = null
  ) {
    val tsTps = readyTsTps(interval, nts, end)
    tsTps.forEach { (tp, ts) ->
      if (vAlphas.isNotEmpty()) {
        optimalProofs.add(ts to VAlways(tp, vAlphas.first().second))
      } else {
        val ltp = sAlphas.lastOrNull()?.second?.at() ?: ltp(tp)
        optimalProofs.add(ts to SatAlways(tp, ltp, sAlphas.map { it.second }.toMutableList()))
      }
      adjustAaux(interval, nts, ntp)
    }
  }

  private fun adjustAaux(interval: RelativeInterval<D>, nts: TS<U, D>, ntp: TP) {
    dropFirstTsTp()
    val (firstTp, firstTS) = firstTsTp() ?: (ntp to nts)

    sAlphas.removeIf { (ts, p) -> ts < (firstTS + interval.startVal) || p.at() < firstTp }
    vAlphas.removeIf { (ts, p) ->
      ts < (interval.startVal?.let { firstTS + it } ?: firstTS) || p.at() < firstTp
    }
    shiftTsTpFuture(interval, firstTS, ntp)
  }
}
