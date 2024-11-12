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

data class MinAux(
    private var sAlphas: MutableList<Pair<TS, SatProof>> = mutableListOf(),
    private var vAlphas: MutableList<Pair<TS, ViolationProof>> = mutableListOf(),
    private var optimalProofs: MutableList<Pair<TS, Proof>> = mutableListOf(),
    val endTS: Double? = null,
) : TAux() {
  fun copy() =
      MinAux(
              sAlphas.map { it.copy() }.toMutableList(),
              vAlphas.map { it.copy() }.toMutableList(),
              optimalProofs.map { it.copy() }.toMutableList(),
              endTS)
          .also {
            it.tsTpOut = tsTpOut.toMutableMap()
            it.tsTpIn = tsTpIn.toMutableMap()
          }
  /** updates the Eaux structure at arrival of a new TS/TP */
  fun updateMinAux(
      interval: Interval,
      nts: TS,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): MutableList<Proof> {
    val iStart = interval.startVal
    val iEnd =
        if (interval is BoundedInterval) interval.endVal else endTS ?: throw UnboundedFuture()
    val adjInterval = BoundedInterval(iStart, iEnd)
    shiftMinAux(adjInterval, nts, ntp, factor)
    addTsTpFuture(adjInterval, nts, ntp)
    addSubps(adjInterval, nts, ntp, p1)

    shiftMinAux(adjInterval, nts, ntp, factor, endTS)
    if (endTS == nts.i) {
      return optimalProofs.map { it.second }.toMutableList()
    } else {
      val result = optimalProofs.filter { (ts, _) -> (ts + iEnd < nts) }
      optimalProofs.removeIf { (ts, _) -> (ts + iEnd < nts) }
      return result.map { it.second }.toMutableList()
    }
  }

  /** updates the Eaux structure at arrival of a new TS/TP */
  fun update(
      interval: Interval,
      nts: TS,
      ntp: TP,
      factor: Double,
      p1: Proof
  ): Pair<MutableList<Proof>, MinAux> {
    val copy = copy()
    val result = copy.updateMinAux(interval, nts, ntp, factor, p1)
    return result to copy
  }

  private fun addSubps(interval: BoundedInterval, nts: TS, ntp: TP, proof: Proof) {
    val firstTS = firstTsTp()?.second?.i ?: 0.0
    when (proof) {
      is SatProof -> {
        if (nts.i >= firstTS + interval.startVal) {
          sAlphas.add(nts to proof)
          sAlphas.sortBy { it.second.size() }
        }
      }
      is ViolationProof -> {
        if (nts.i >= firstTS + interval.startVal) vAlphas.add(nts to proof)
      }
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftMinAux(
      interval: BoundedInterval,
      nts: TS,
      ntp: TP,
      factor: Double,
      end: Double? = null
  ) {
    val tsTps = readyTsTps(interval, nts, end)
    tsTps.forEach { (tp, ts) ->
      if (vAlphas.isEmpty()) {
        optimalProofs.add(ts to SatPastMinAllSat(tp))
      }
      val correct = sAlphas.size.toDouble() / (sAlphas.size + vAlphas.size)
      if (correct >= factor) {
        val ltp = sAlphas.lastOrNull()?.second?.at() ?: ltp(tp)
        optimalProofs.add(
            ts to SatPastMinPrev(tp, ltp, correct, sAlphas.map { it.second }.toMutableList()))
      } else {
        optimalProofs.add(
            ts to
                VPastMinPrev(
                    tp,
                    vAlphas.last().second.at(),
                    correct,
                    vAlphas.map { it.second }.toMutableList()))
      }
      adjustMinAux(interval, nts, ntp)
    }
  }

  private fun adjustMinAux(interval: BoundedInterval, nts: TS, ntp: TP) {
    dropFirstTsTp()
    val (firstTp, firstTs) = firstTsTp() ?: (ntp to nts)

    sAlphas.removeIf { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp }
    vAlphas.removeIf { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp }
    shiftTsTpFuture(interval, firstTs, ntp)
  }
}
