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

class Eaux(
  private var sAlphas: MutableList<Pair<TS, SatProof>> = mutableListOf(),
  private var vAlphas: MutableList<Pair<TS, Proof>> = mutableListOf(),
  private var optimalProofs: MutableList<Pair<TS, Proof>> = mutableListOf(),
  val endTS: Double? = null,
) : TAux() {
  /** updates the Eaux structure at arrival of a new TS/TP */
  fun updateEaux(interval: Interval, nts: TS, ntp: TP, p1: Proof): MutableList<Proof> {
    val iStart = interval.startVal
    val iEnd =
      if (interval is BoundedInterval) interval.endVal else endTS ?: throw UnboundedFuture()
    val adjInterval = BoundedInterval(iStart, iEnd)
    shiftEaux(adjInterval, nts, ntp)
    addTsTpFuture(adjInterval, nts, ntp)
    addSubps(adjInterval, nts, ntp, p1)

    shiftEaux(adjInterval, nts, ntp, endTS)
    if (endTS == nts.i) {
      return optimalProofs.map { it.second }.toMutableList()
    } else {
      val result = optimalProofs.filter { (ts, _) -> (ts + iEnd < nts) }
      optimalProofs.removeIf { (ts, _) -> (ts + iEnd < nts) }
      return result.map { it.second }.toMutableList()
    }
  }

  fun copy() =
    Eaux(
      sAlphas.map { it.copy() }.toMutableList(),
      vAlphas.map { it.copy() }.toMutableList(),
      optimalProofs.map { it.copy() }.toMutableList(),
      endTS)
      .also {
        it.tsTpOut = tsTpOut.toMutableMap()
        it.tsTpIn = tsTpIn.toMutableMap()
      }

  fun update(interval: Interval, nts: TS, ntp: TP, p1: Proof): Pair<MutableList<Proof>, Eaux> {
    val copy = copy()
    val result = copy.updateEaux(interval, nts, ntp, p1)
    return result to copy
  }

  private fun addSubps(interval: BoundedInterval, nts: TS, ntp: TP, proof: Proof) {
    val firstTS = firstTsTp()?.second?.i ?: 0.0
    when (proof) {
      is SatProof -> {
        if (nts.i >= firstTS + interval.startVal) {
          sAlphas.add(nts to proof)
        }
      }
      is ViolationProof -> {
        if (nts.i >= firstTS + interval.startVal) {
          vAlphas.add(nts to proof)
        }
      }
      else -> throw InvalidProofObject()
    }
  }

  private fun shiftEaux(interval: BoundedInterval, nts: TS, ntp: TP, end: Double? = null) {
    val tsTps = readyTsTps(interval, nts, end)
    tsTps.entries.forEach { (tp, ts) ->
      if (sAlphas.isNotEmpty()) {
        optimalProofs.add(ts to SatEventually(tp, sAlphas.minBy { it.second.size() }.second))
      } else {
        val ltp = vAlphas.lastOrNull()?.second?.at() ?: ltp(tp)
        optimalProofs.add(ts to VEventually(tp, ltp, vAlphas.map { it.second }.toMutableList()))
      }
      adjustEaux(interval, nts, ntp)
    }
  }

  private fun adjustEaux(interval: BoundedInterval, nts: TS, ntp: TP) {
    dropFirstTsTp()
    val (firstTp, firstTs) = firstTsTp() ?: (ntp to nts)

    sAlphas = sAlphas.filterNot { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp  }.toMutableList()
    //sAlphas.removeIf { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp }
    vAlphas = vAlphas.filterNotTo(mutableListOf()) { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp }
      //vAlphas.removeIf { (ts, p) -> ts < (firstTs + interval.startVal) || p.at() < firstTp }
    shiftTsTpFuture(interval, firstTs, ntp)
  }
}
