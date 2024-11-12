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
import tools.aqua.*
import tools.aqua.stars.carla.experiments.komfymc.*

/**
 * @param tsZero starting timestamp (needed for when the interval denotes not existing timestamps)
 * @param tsTpIn TS-TP pairs that are inside the interval
 * @param tsTpOut TS-TP pairs that are outside the interval
 * @param sBetaAlphasIn stores SatProofs inside the interval
 * @param sBetaAlphasOut stores SatProofs outside the interval
 * @param vAlphaBetasIn stores VProofs of type S⁻ where alpha is violated and beta is violated
 *   starting from that point until the end of the interval
 * @param vAlphasOut stores VProofs of type S⁻ where alpha is violated outside the interval
 * @param vBetasIn stores VProofs of type S⁻inf where beta is violated inside the entire interval
 * @param vAlphasBetasOut stores all violations of alpha and beta outside the interval (are shifted
 *   into other violation proofs as needed)
 */
class Saux(
    private var tsZero: TS? = null,
    private var sBetaAlphasIn: MutableList<Pair<TS, SatSince>> = mutableListOf(),
    private var sBetaAlphasOut: MutableList<Pair<TS, SatSince>> = mutableListOf(),
    private var vAlphaBetasIn: MutableList<Pair<TS, VSince>> = mutableListOf(),
    private var vAlphasOut: MutableList<Pair<TS, ViolationProof>> = mutableListOf(),
    private var vBetasIn: MutableList<Pair<TS, ViolationProof>> = mutableListOf(),
    private var vAlphasBetasOut: MutableList<Triple<TS, ViolationProof?, ViolationProof?>> =
        mutableListOf(),
) : TAux() {

  /** function to be called from outside at every new timestamp */
  fun updateSaux(interval: Interval, ts: TS, tp: TP, p1: Proof, p2: Proof): Proof {
    val currTsZero = tsZero ?: ts
    tsZero = currTsZero
    addSubps(ts, p1, p2)
    if (ts.i < (currTsZero.i + interval.startVal)) {
      tsTpOut[tp] = ts
      return VSinceOutL(tp)
    } else {
      val l = if (interval is BoundedInterval) max(0.0, (ts.i - interval.endVal)) else currTsZero.i
      val r = ts.i - interval.startVal
      shiftSaux(BoundedInterval(l, r), interval.startVal, ts, tp)
      return evalSaux(tp)
    }
  }
  /** extracts a proof of minimal size */
  private fun evalSaux(tp: TP): Proof {
    if (sBetaAlphasIn.isNotEmpty()) {
      return sBetaAlphasIn.first().second
    }

    val p = mutableListOf<Proof>()
    vAlphaBetasIn.firstOrNull()?.second?.let { p.add(it) }
    vAlphasOut.firstOrNull()?.second?.let { p.add(VSince(tp, it, mutableListOf())) }
    if (vBetasIn.size == tsTpIn.size) {
      val etp =
          if (vBetasIn.isEmpty()) {
            etp(tp)
          } else {
            vBetasIn.first().second.at()
          }
      val betasSuffix: MutableList<Proof> = vBetasIn.map { it.second }.reversed().toMutableList()
      p.add(VSinceInf(tp, etp, betasSuffix))
    }
    return p.reduce { acc, proof -> if (acc.size() <= proof.size()) acc else proof }
  }

  /**
   * update sBetaAlphasIn, sBetaAlphasOut, vAlphasOut, vAlphasBetasOut according to proof at current
   * TS/TP
   */
  private fun addSubps(ts: TS, p1: Proof, p2: Proof) {
    when {
      p1 is SatProof && p2 is SatProof -> {
        // TODO als ForEach
        sBetaAlphasIn.forEachIndexed { i, _ -> sBetaAlphasIn[i].second.alphas.add(p1) }
        sBetaAlphasOut.forEachIndexed { i, _ -> sBetaAlphasOut[i].second.alphas.add(p1) }

        sBetaAlphasOut.add(ts to SatSince(p2))
        vAlphasBetasOut.add(Triple(ts, null, null))
      }
      p1 is SatProof && p2 is ViolationProof -> {
        sBetaAlphasIn.forEachIndexed { i, _ -> sBetaAlphasIn[i].second.alphas.add(p1) }
        sBetaAlphasOut.forEachIndexed { i, _ -> sBetaAlphasOut[i].second.alphas.add(p1) }

        vAlphasBetasOut.add(Triple(ts, null, p2)) // add violating proofs
      }
      p1 is ViolationProof && p2 is SatProof -> {
        sBetaAlphasIn.clear()
        sBetaAlphasOut.clear()

        sBetaAlphasOut.add(ts to SatSince(p2))
        // keep the smallest proofs in vAlphasOut, add p1 (will be removed next time if it is bigger
        // than other proofs)
        vAlphasOut.removeIf { (_, proof) -> proof > p1 }
        vAlphasOut.add(ts to p1)
        vAlphasBetasOut.add(Triple(ts, p1, null)) // add violating proofs
      }
      p1 is ViolationProof && p2 is ViolationProof -> {
        sBetaAlphasIn.clear()
        sBetaAlphasOut.clear()
        // keep the smallest proofs in vAlphasOut, add p1 (will be removed next time if it is bigger
        // than other proofs)
        vAlphasOut.removeIf { (_, proof) -> proof > p1 }
        vAlphasOut.add(ts to p1)

        vAlphasBetasOut.add(Triple(ts, p1, p2)) // add violating proofs
      }
    }
  }

  /**
   * shifts the interval and the proofs around in the different proof lists according to the current
   * TS/TP
   */
  private fun shiftSaux(interval: BoundedInterval, iStart: Double, nts: TS, ntp: TP) {
    shiftTsTpsPast(interval, iStart, nts, ntp)

    // updates sBetaAlphasOut and sBetaAlphasIn
    val shiftToIn = sBetaAlphasOut.filter { (ts, _) -> interval.contains(ts.i) }
    if (shiftToIn.isNotEmpty()) {
      sBetaAlphasIn.addAll(shiftToIn)
      sBetaAlphasIn.sortBy { it.second.size() }
    }
    sBetaAlphasOut.removeIf { (ts, _) -> ts.i <= interval.endVal }

    // updates vBetasIn and vAlphaBetasIn
    val newVioIn = vAlphasBetasOut.filter { (ts, _, _) -> interval.contains(ts.i) }
    vAlphasBetasOut.removeIf { (ts, _) -> ts.i <= interval.endVal }

    newVioIn.forEach { (ts, _, vp2) ->
      when (vp2) {
        null -> vBetasIn.clear()
        else -> vBetasIn.add(ts to vp2)
      }
    }
    /** functionality of update_v_alpha_betas_in (including construct_vsinceps) */
    newVioIn.forEach { (ts, vp1, vp2) ->
      when (vp2) {
        null -> vAlphaBetasIn.clear()
        else -> {
          vAlphaBetasIn.forEachIndexed { i, _ -> vAlphaBetasIn[i].second.vBetas.add(vp2) }
          if (vp1 != null) {
            vAlphaBetasIn.add(ts to VSince(ntp, vp1, mutableListOf(vp2)))
          }
        }
      }
    }

    if (vAlphaBetasIn.isNotEmpty()) vAlphaBetasIn.sortBy { it.second.size() }
    vAlphaBetasIn.forEachIndexed { i, _ -> vAlphaBetasIn[i].second.tp = ntp }

    // remove_from_msaux
    sBetaAlphasIn.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    vAlphaBetasIn.removeIf { (tsL, _) -> tsL.i < interval.startVal }
    vAlphasOut.removeIf { (tsL, _) -> tsL.i <= interval.endVal }
    vBetasIn.removeIf { (tsL, _) -> tsL.i < interval.startVal }
  }

  fun copy() =
      Saux(
              tsZero,
              sBetaAlphasIn.map { it.copy() }.toMutableList(),
              sBetaAlphasOut.map { it.copy() }.toMutableList(),
              vAlphaBetasIn.map { it.copy() }.toMutableList(),
              vAlphasOut.map { it.copy() }.toMutableList(),
              vBetasIn.map { it.copy() }.toMutableList(),
              vAlphasBetasOut.map { it.copy() }.toMutableList())
          .also {
            it.tsTpOut = tsTpOut.toMutableMap()
            it.tsTpIn = tsTpIn.toMutableMap()
          }

  fun update1(interval: Interval, nts: TS, ntp: TP, p1: Proof, p2: Proof): Pair<Proof, Saux> {
    val copy = copy()
    val result = copy.updateSaux(interval, nts, ntp, p1, p2)
    return result to copy
  }
}
