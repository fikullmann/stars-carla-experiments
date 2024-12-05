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
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

class PrevNext<U : TickUnit<U, D>, D : TickDifference<D>>() {
  companion object {
    fun <U : TickUnit<U, D>, D : TickDifference<D>> prevNext(
        prev: Boolean,
        interval: RelativeInterval<D>,
        buf: MutableList<Proof>,
        timestamps: MutableList<TS<U, D>>
    ): Triple<MutableList<Proof>, List<Proof>, MutableList<TS<U, D>>> {
      return if (buf.isEmpty()) Triple(mutableListOf(), listOf(), timestamps)
      else if (timestamps.isEmpty()) Triple(mutableListOf(), buf, mutableListOf())
      else if (timestamps.size == 1) Triple(mutableListOf(), buf, timestamps)
      else prevNextAux(prev, interval, buf, timestamps)
    }

    private fun <U : TickUnit<U, D>, D : TickDifference<D>> prevNextAux(
        prev: Boolean,
        interval: RelativeInterval<D>,
        buf: MutableList<Proof>,
        timestamps: MutableList<TS<U, D>>
    ): Triple<MutableList<Proof>, List<Proof>, MutableList<TS<U, D>>> {
      val t1 = timestamps.removeFirst().i
      val t2 = timestamps.first().i
      val t = t2 - t1
      val p = buf.removeFirst()
      val (ps, buf1, tss1) = prevNext(prev, interval, buf, timestamps)
      val prevNextProofs: MutableList<Proof> =
          when {
            p is SatProof && interval.contains(t) ->
                mutableListOf(if (prev) SatPrev(p) else SatNext(p))
            p is ViolationProof -> mutableListOf(if (prev) VPrev(p) else VNext(p))
            else -> mutableListOf()
          }
      if (interval.below(t)) {
        prevNextProofs.add(if (prev) VPrevOutL(p.at() + 1) else VNextOutL(p.at() - 1))
      } else if (interval.above(t)) {
        prevNextProofs.add(if (prev) VPrevOutR(p.at() + 1) else VNextOutR(p.at() - 1))
      }
      ps.add(
          0,
          prevNextProofs.reduce { acc, newProof ->
            if (acc.size() < newProof.size()) acc else newProof
          })
      return Triple(ps, buf1, tss1)
    }
  }
}

class MPrevNext {
  companion object {
    fun <U : TickUnit<U, D>, D : TickDifference<D>> update(
        prev: Boolean,
        interval: RelativeInterval<D>,
        p: Proof,
        t1: TS<U, D>,
        t2: TS<U, D>,
    ): Proof {
      val t = (t2.i - t1.i)
      val prevNextProofs: MutableList<Proof> =
          when {
            p is SatProof && interval.contains(t) ->
                mutableListOf(if (prev) SatPrev(p) else SatNext(p))
            p is ViolationProof -> mutableListOf(if (prev) VPrev(p) else VNext(p))
            else -> mutableListOf()
          }
      if (interval.below(t)) {
        prevNextProofs.add(if (prev) VPrevOutL(p.at() + 1) else VNextOutL(p.at() - 1))
      } else if (interval.above(t)) {
        prevNextProofs.add(if (prev) VPrevOutR(p.at() + 1) else VNextOutR(p.at() - 1))
      }
      return minpList(prevNextProofs)
    }
  }
}
