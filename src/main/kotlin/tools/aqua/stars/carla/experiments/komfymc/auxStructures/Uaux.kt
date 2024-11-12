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
import tools.aqua.stars.carla.experiments.komfymc.*

var i = 0

class Uaux(
    private var sAlphasBeta: MutableList<MutableList<Pair<TS, SatUntil>>> =
        mutableListOf(mutableListOf()),
    private var sAlphasSuffix: MutableList<Pair<TS, SatProof>> = mutableListOf(),
    private var vBetasAlpha: MutableList<MutableList<Pair<TS, VUntil>>> =
        mutableListOf(mutableListOf()),
    private var vAlphasOut: MutableList<Pair<TS, Proof>> = mutableListOf(),
    private var vAlphasIn: MutableList<Pair<TS, Proof>> = mutableListOf(),
    private var vBetasSuffixIn: MutableList<Pair<TS, ViolationProof>> = mutableListOf(),
    private var optimalProofs: MutableList<Pair<TS, Proof>> = mutableListOf(),
    val endTS: Double? = null
) : TAux() {
  /** updates the Uaux structure at arrival of a new TS/TP */
  fun updateUaux(interval: Interval, nts: TS, ntp: TP, p1: Proof, p2: Proof): MutableList<Proof> {
    i++
    if (i % 155 == 1) {
      print("")
    }
    val iStart = interval.startVal
    val iEnd =
        if (interval is BoundedInterval) interval.endVal else endTS ?: throw UnboundedFuture()
    val adjInterval = BoundedInterval(iStart, iEnd)
    shiftUaux(adjInterval, nts, ntp)
    addTsTpFuture(adjInterval, nts, ntp)
    addSubps(adjInterval, nts, ntp, p1, p2)

    shiftUaux(adjInterval, nts, ntp, endTS)
    if (endTS == nts.i) {
      return optimalProofs.map { it.second }.toMutableList()
    } else {
      val result = optimalProofs.filter { (ts, _) -> (ts + iEnd < nts) }
      optimalProofs.removeIf { (ts, _) -> (ts + iEnd < nts) }
      return result.map { it.second }.toMutableList()
    }
  }

  private fun addSubps(interval: BoundedInterval, nts: TS, ntp: TP, p1: Proof, p2: Proof) {
    val firstTS = firstTsTp()?.second?.i ?: 0.0
    when {
      p1 is SatProof && p2 is SatProof -> {
        if (nts.i >= firstTS + interval.startVal) {
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
        if (nts.i >= firstTS + interval.startVal) {
          vBetasSuffixIn.add(nts to p2)
        }
      }
      p1 is ViolationProof && p2 is SatProof -> {
        if (nts.i >= firstTS + interval.startVal) {
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
          vAlphasIn.add(nts to p1)
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

        if (nts.i >= firstTS + interval.startVal) {
          // vBetasSuffixIn
          vBetasSuffixIn.add(nts to p2)
          // vBetasAlpha
          val curBetasAlphas = vBetasAlpha.removeLast()
          curBetasAlphas.add(
              nts to VUntil(ntp, p1, vBetasSuffixIn.map { it.second }.toMutableList()))
          curBetasAlphas.sortBy { it.second.at() }
          vBetasAlpha.add(curBetasAlphas)

          // vAlphasIn
          vAlphasIn.add(nts to p1)
        } else {
          vAlphasOut.removeIf { (_, proof) -> proof >= p1 }
          vAlphasOut.add(nts to p1)
        }
      }
    }
  }

  private fun shiftUaux(interval: BoundedInterval, nts: TS, ntp: TP, end: Double? = null) {
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

        if (proofs.isEmpty()) {
          println(i)
          println("ayayay ${nts.i}")
        }
        val minProof =
            proofs.reduce { acc, newProof -> if (acc.size() < newProof.size()) acc else newProof }
        optimalProofs.add(ts to minProof)
      }
      adjustUaux(interval, nts, ntp)
    }
  }

  private fun adjustUaux(interval: BoundedInterval, nts: TS, ntp: TP) {
    val evalTp = firstTsTp()?.first ?: throw NoExistingTsTp()
    dropFirstTsTp()
    val (firstTp, firstTs) = firstTsTp() ?: (ntp to nts)

    // vBetasAlpha
    vBetasAlpha.forEachIndexed { i, proofList ->
      vBetasAlpha[i] =
          proofList
              .dropWhile { (ts, proof) ->
                ts < firstTs + interval.startVal || (proof.at() < firstTp)
              }
              .toMutableList()
    }
    if (abs(interval.startVal) < 0.0001) {
      dropUauxSingleTs(evalTp)
    } else dropUauxTs(interval.startVal, firstTs)
    vBetasAlpha = vBetasAlpha.dropWhile { d -> d.isEmpty() }.toMutableList()
    if (vBetasAlpha.isEmpty()) {
      vBetasAlpha.add(mutableListOf())
    }
    // tstpin and out
    shiftTsTpFuture(interval, firstTs, ntp)

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
    sAlphasBeta.iterator().forEach { proofList ->
      proofList.dropWhile { (_, p) -> tsTpOf(p.beta.at()) < (firstTs + interval.startVal) }
    }
    sAlphasBeta = sAlphasBeta.dropWhile { it.isEmpty() }.toMutableList()
    if (sAlphasBeta.isEmpty()) {
      sAlphasBeta.add(mutableListOf())
    }

    // alphas suffix
    sAlphasSuffix.removeIf { (_, proof) -> proof.at() < firstTp }

    // alphas_in and v_alphas_out
    vAlphasOut.removeIf { (_, proof) -> proof.at() < firstTp }
    val shiftToOut: List<Pair<TS, Proof>> =
        vAlphasIn.filter { (ts, _) -> ts < firstTs + interval.startVal && ts >= firstTs }
    vAlphasIn.removeIf { (ts, _) -> ts < firstTs + interval.startVal }
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
    val firstBetaAlpha = mutableListOf<Pair<TS, VUntil>>()
    vBetasAlpha.removeFirst().forEach { (vts, vp) ->
      if (etp(vp) <= evalTP && vp.vBetas.size > 1) {
        vp.vBetas.removeFirst()
        firstBetaAlpha.add(vts to vp)
      } else {
        firstBetaAlpha.add(vts to vp)
      }
    }
    vBetasAlpha.add(0, firstBetaAlpha)
  }

  private fun dropUauxTs(a: Double, firstTs: TS) {
    vBetasAlpha.forEachIndexed { i, curBetasAlpha ->
      vBetasAlpha[i] =
          curBetasAlpha.fold(mutableListOf()) { acc, (vts, vp) ->
            // check if earliest timepoint in vProof is outside the bound (firstTs + a)
            // if out then remove the earliest part of the proof
            do {
              var isOut = tsTpIn[etp(vp)]?.let { ts -> ts < (firstTs + a) } ?: true
              if (vp.vBetas.size > 1) vp.vBetas.removeFirst() else isOut = false
            } while (isOut)
            if (vp.vBetas.size > 1) acc.add(vts to vp)
            acc
          }
    }
  }

  private fun etp(vp: VUntil) = if (vp.vBetas.isEmpty()) vp.tp else vp.vBetas.first().at()

  fun copy() =
      Uaux(
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
              optimalProofs.map { it.copy() }.toMutableList(),
              endTS)
          .also {
            it.tsTpOut = tsTpOut.toMutableMap()
            it.tsTpIn = tsTpIn.toMutableMap()
          }

  fun update1(
      interval: Interval,
      nts: TS,
      ntp: TP,
      p1: Proof,
      p2: Proof
  ): Pair<MutableList<Proof>, Uaux> {
    val copy = copy()
    val result = copy.updateUaux(interval, nts, ntp, p1, p2)
    return result to copy
  }
}
