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

var i = 0

class Uaux<U : TickUnit<U, D>, D : TickDifference<D>>(
    endTS: TS<U, D>? = null,
    private var sAlphasBeta: MutableList<MutableList<Pair<TS<U, D>, SatUntil>>> =
        mutableListOf(mutableListOf()),
    private var sAlphasSuffix: MutableList<Pair<TS<U, D>, SatProof>> = mutableListOf(),
    private var vBetasAlpha: MutableList<MutableList<Pair<TS<U, D>, VUntil>>> =
        mutableListOf(mutableListOf()),
    private var vAlphasOut: MutableList<Pair<TS<U, D>, Proof>> = mutableListOf(),
    private var vAlphasIn: MutableList<Pair<TS<U, D>, Proof>> = mutableListOf(),
    private var vBetasSuffixIn: MutableList<Pair<TS<U, D>, ViolationProof>> = mutableListOf(),

) : FutureAux<U, D>(endTS) {
  /** updates the Uaux structure at arrival of a new TS/TP */
  fun updateUaux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      p1: Proof,
      p2: Proof
  ): MutableList<Proof> {
    shiftUaux(interval, nts, ntp)
    addTsTpFuture(interval, nts, ntp)
    addSubps(interval, nts, ntp, p1, p2)

    shiftUaux(interval, nts, ntp, endTS)
    return findOptimalProofs(interval, nts)
  }

  private fun addSubps(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      p1: Proof,
      p2: Proof
  ) {
    val firstTS = firstTsTp()?.second ?: throw NoExistingTsTp()
    when {
      p1 is SatProof && p2 is SatProof -> {
        if (nts >= firstTS + interval.startVal) {
          // sAlphasBeta
          val currAlphasBeta = sAlphasBeta.removeLast()
          currAlphasBeta.add(nts to SatUntil(p2, sAlphasSuffix.map { it.second }.toMutableList()))
          currAlphasBeta.sortBy { it.second.at() }
          sAlphasBeta.add(currAlphasBeta)

          // vBetasIn
          vBetasSuffixIn.clear()
        }
        // vBetasAlpha
        if (vBetasAlpha.last().isNotEmpty()) {
          vBetasAlpha.add(mutableListOf())
        }
        // sAlphasSuffix
        sAlphasSuffix.add(nts to p1)
      }
      p1 is SatProof && p2 is ViolationProof -> {
        // sAlphasSuffix
        sAlphasSuffix.add(nts to p1)
        // vBetasIn
        if (nts >= firstTS + interval.startVal) {
          vBetasSuffixIn.add(nts to p2)
        }
      }
      p1 is ViolationProof && p2 is SatProof -> {
        if (nts >= firstTS + interval.startVal) {
          // sAlphasBeta
          val currAlphasBeta = sAlphasBeta.removeLast()
          currAlphasBeta.add(nts to SatUntil(p2, sAlphasSuffix.map { it.second }.toMutableList()))
          currAlphasBeta.sortBy { it.second.at() }
          sAlphasBeta.add(currAlphasBeta)

          // sAlphasBeta
          if (sAlphasBeta.last().isNotEmpty()) {
            sAlphasBeta.add(mutableListOf())
          }
          // vBetasSuffixIn
          vBetasSuffixIn.clear()
          // vAlphasIn
          if (interval.startVal != null) vAlphasIn.add(nts to p1)
        } else {
          vAlphasOut.removeIf { (_, proof) -> proof >= p1 }
          vAlphasOut.add(nts to p1)
        }
        // vBetasAlpha
        if (vBetasAlpha.last().isNotEmpty()) {
          vBetasAlpha.add(mutableListOf())
        }

        // sAlphasSuffix
        sAlphasSuffix.clear()
      }
      p1 is ViolationProof && p2 is ViolationProof -> {
        if (sAlphasBeta.last().isNotEmpty()) {
          sAlphasBeta.add(mutableListOf())
        }
        sAlphasSuffix.clear()

        if (nts >= firstTS + interval.startVal) {
          // vBetasSuffixIn
          vBetasSuffixIn.add(nts to p2)
          // vBetasAlpha
            if (vBetasSuffixIn.size < tsTpIn.size) {
                val curBetasAlphas = vBetasAlpha.removeLast()
                curBetasAlphas.add(nts to VUntil(ntp, p1, vBetasSuffixIn.map { it.second }.toMutableList()))
                curBetasAlphas.sortBy { it.second.at() }
                vBetasAlpha.add(curBetasAlphas)
            } else {
                vBetasAlpha.removeLast()
                vBetasAlpha.add(mutableListOf(nts to VUntil(ntp, p1, vBetasSuffixIn.map { it.second }.toMutableList())))
            }

          // vAlphasIn
          if (interval.startVal != null) vAlphasIn.add(nts to p1)
        } else {
          vAlphasOut.removeIf { (_, proof) -> proof >= p1 }
          vAlphasOut.add(nts to p1)
        }
      }
    }
  }

  private fun shiftUaux(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      end: TS<U, D>? = null
  ) {
    val tsTps = readyTsTps(interval, nts, end)
    tsTps.forEach { (tp, ts) ->
      val optimalProofsLen = optimalProofs.size

      // sAlphasBetas
      val frontAlphasBetas = sAlphasBeta.first()
      if (frontAlphasBetas.isNotEmpty()) {
        val satProof = frontAlphasBetas.first().second
        if (tp == satProof.at()) {
          optimalProofs.add(ts to SatUntil(satProof.beta, satProof.alphas.toMutableList()))
        }
      }
      if (optimalProofsLen == optimalProofs.size) {
        val proofs = mutableListOf<Proof>()
        val vBAProof = vBetasAlpha.firstOrNull()?.firstOrNull()?.second
        if (vBAProof != null) {
          if (etp(vBAProof) == tsTpIn.entries.firstOrNull()?.key) {
            proofs.add(VUntil(tp, vBAProof.vAlpha, mutableListOf()))
          }
        }
        if (vAlphasOut.isNotEmpty()) {
          val vAOProof = vAlphasOut.first().second
          proofs.add(VUntil(tp, vAOProof, mutableListOf()))
        }
        if (vBetasSuffixIn.size == tsTpIn.size) {
            val ltp = vBetasSuffixIn.lastOrNull()?.second?.at() ?: ltp(tp)
            proofs.add(VUntilInf(tp, ltp, vBetasSuffixIn.map { it.second }.toMutableList()))
        }

        val minProof =
            proofs.reduce { acc, newProof -> if (acc.size() < newProof.size()) acc else newProof }
        optimalProofs.add(ts to minProof)
      }
      adjustUaux(interval, nts, ntp)
    }
  }

  private fun adjustUaux(interval: RelativeInterval<D>, nts: TS<U, D>, ntp: TP) {
    val evalTp = firstTsTp()?.first ?: throw NoExistingTsTp()
    dropFirstTsTp()
    val (firstTp, firstTS) = firstTsTp() ?: (ntp to nts)

    // vBetasAlpha
    vBetasAlpha.mutate { proofList ->
          proofList.filterNot { (ts, proof) ->
                ts < (interval.startVal?.let { firstTS + it } ?: firstTS) || (proof.at() < firstTp)
              }.toMutableList()
    }
    if (interval.startVal == null) {
      dropUauxSingleTs(evalTp)
    } else dropUauxTs(interval.startVal, firstTS)
    vBetasAlpha = vBetasAlpha.filter { d -> d.isNotEmpty() }.toMutableList()
    if (vBetasAlpha.isEmpty()) {
      vBetasAlpha.add(mutableListOf())
    }
    // tstpin and out
    shiftTsTpFuture(interval, firstTS, ntp)

    // alphas beta
    sAlphasBeta.first().let { frontAlphasBeta ->
      if (frontAlphasBeta.isNotEmpty()) {
        // for proofs where tp == evalTP: remove First proof (if not possible then remove entirely)
        sAlphasBeta[0] =
            frontAlphasBeta
                .filter { (_, satProof) -> evalTp != satProof.at() || satProof.alphas.isNotEmpty() }
                .map { (ts, satProof) ->
                  if (evalTp == satProof.at()) {
                    satProof.alphas.removeFirst()
                  }
                  ts to satProof
                }
                .toMutableList()
      }
    }
    sAlphasBeta.forEachIndexed { i, proofList ->
      sAlphasBeta[i] =
          proofList
              .dropWhile { (_, p) ->
                tsTpOf(p.beta.at()) < (interval.startVal?.let { firstTS + it } ?: firstTS)
              }
              .toMutableList()
    }
    sAlphasBeta = sAlphasBeta.dropWhile { it.isEmpty() }.toMutableList()
    if (sAlphasBeta.isEmpty()) {
      sAlphasBeta.add(mutableListOf())
    }

    // alphas suffix
    sAlphasSuffix = sAlphasSuffix.filterNot { (_, proof) -> proof.at() < firstTp }.toMutableList()

    // alphas_in and v_alphas_out
    vAlphasOut = vAlphasOut.filterNot { (_, proof) -> proof.at() < firstTp }.toMutableList()
    val shiftToOut: List<Pair<TS<U, D>, Proof>> =
        vAlphasIn.filter { (ts, _) ->
          ts < (interval.startVal?.let { firstTS + it } ?: firstTS) && ts >= firstTS
        }
    vAlphasIn = vAlphasIn.filterNot { (ts, _) -> ts < (interval.startVal?.let { firstTS + it } ?: firstTS) }.toMutableList()
    if (shiftToOut.isNotEmpty()) {
      vAlphasOut.addAll(shiftToOut)
      vAlphasOut.sortBy { it.second.size() }
    }
    // vBetasIn
    vBetasSuffixIn =
        vBetasSuffixIn
            .dropWhile { (_, vp) ->
              when (tsTpIn.entries.firstOrNull()) {
                null ->
                    when (tsTpOut.entries.firstOrNull()) {
                      null -> vp.at() <= ntp
                      else -> vp.at() <= tsTpOut.keys.first()
                    }
                else -> vp.at() < tsTpIn.keys.first()
              }
            }
            .toMutableList()
  }

  private fun dropUauxSingleTs(evalTP: TP) {
    val firstBetaAlpha = mutableListOf<Pair<TS<U, D>, VUntil>>()
    vBetasAlpha.removeFirst().forEach { (vts, vp) ->
      if (etp(vp) <= evalTP && vp.vBetas.size > 1) {
        vp.vBetas.removeFirst()
        firstBetaAlpha.add(vts to vp)
      } else {
        firstBetaAlpha.add(0, vts to vp)
      }
      vBetasAlpha.add(0, firstBetaAlpha)
    }
  }

  private fun dropUauxTs(a: D?, firstTs: TS<U, D>) {
    vBetasAlpha.forEachIndexed { i, curBetasAlpha ->
      vBetasAlpha[i] =
          curBetasAlpha.fold(mutableListOf()) { acc, (vts, vp) ->
            // check if earliest timepoint in vProof is outside the bound (firstTs + a)
            // if out then remove the earliest part of the proof
            do {
              var isOut =
                  tsTpIn[etp(vp)]?.let { ts -> if (a != null) ts < (firstTs + a) else false }
                      ?: true
              if (vp.vBetas.size > 1) vp.vBetas.removeFirst() else isOut = false
            } while (isOut)
            if (vp.vBetas.size > 1) acc.add(vts to vp)
            acc
          }
    }
  }

  private fun etp(vp: VUntil) = if (vp.vBetas.isEmpty()) vp.tp else vp.vBetas.first().at()

  fun copy() =
      Uaux(endTS,
              sAlphasBeta
                  .map { it.map { inside -> inside.copy() }.toMutableList() }
                  .toMutableList(),
              sAlphasSuffix.map { it.copy() }.toMutableList(),
              vBetasAlpha
                  .map { it.map { inside -> inside.copy() }.toMutableList() }
                  .toMutableList(),
              vAlphasOut.map { it.copy() }.toMutableList(),
              vAlphasIn.map { it.copy() }.toMutableList(),
              vBetasSuffixIn.map { it.copy() }.toMutableList(),
              )
          .also { aux ->
            aux.tsTpOut = tsTpOut.toMutableMap()
            aux.tsTpIn = tsTpIn.toMutableMap()
            aux.optimalProofs = aux.optimalProofs.map { it.copy() }.toMutableList()
          }

  fun update1(
      interval: RelativeInterval<D>,
      nts: TS<U, D>,
      ntp: TP,
      p1: Proof,
      p2: Proof
  ): Pair<MutableList<Proof>, Uaux<U, D>> {
      if (ntp.i == 0) {
          val copy = copy()
          val result = copy.updateUaux(interval, nts, ntp, p1, p2)
          return result to copy
      } else {
          return updateUaux(interval, nts, ntp, p1, p2) to this
      }
  }
}
