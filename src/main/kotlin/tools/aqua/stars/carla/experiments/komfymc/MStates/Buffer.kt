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

package tools.aqua.stars.carla.experiments.komfymc.MStates

import tools.aqua.auxStructures.MPrevNext
import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

class Buf2(
    val lhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val rhs: MutableList<Pdt<Proof>> = mutableListOf()
) {
  fun add(p1s: List<Pdt<Proof>>, p2s: List<Pdt<Proof>>) {
    lhs.addAll(p1s)
    rhs.addAll(p2s)
  }

  fun take(
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>,
      f: (Proof, Proof) -> Proof
  ): List<Pdt<Proof>> {
      val result = mutableListOf<Pdt<Proof>>()
      while (lhs.isNotEmpty() && rhs.isNotEmpty()) {
          val expl1 = lhs.removeFirst()
          val expl2 = rhs.removeFirst()
          result.add(apply2(ref, expl1, expl2) { p1, p2 -> f(p1, p2) })
      }
      return result
  }
}

class BufPrevNext<U : TickUnit<U, D>, D : TickDifference<D>>(
    val inner: MutableList<Pdt<Proof>> = mutableListOf(),
    val tss: MutableList<TS<U, D>> = mutableListOf()
) {
  fun clearInner() {
    inner.clear()
  }

  fun add(p1s: List<Pdt<Proof>>, ts: TS<U, D>) {
    inner.addAll(p1s)
    tss.add(ts)
  }

  fun take(
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>,
      prev: Boolean,
      interval: RelativeInterval<D>
  ): List<Pdt<Proof>> {
    val result = mutableListOf<Pdt<Proof>>()
      while (inner.isNotEmpty() && tss.size > 1) {
          val expl = inner.removeFirst()
          val ts1 = tss.removeFirst()
          val ts2 = tss.first()
          result.add(expl.apply1(ref) { p ->
              MPrevNext.update(prev, interval, p, ts1, ts2)
          })
      }
    return result
  }
}

class BufT<U : TickUnit<U, D>, D : TickDifference<D>>(
    val inner: MutableList<Pdt<Proof>> = mutableListOf(),
    val tsTp: MutableList<Pair<TS<U, D>, TP>> = mutableListOf()
) : Iterable<Triple<Pdt<Proof>, TS<U, D>, TP>> {
  fun clearInner() {
    inner.clear()
  }

  fun add(p1s: List<Pdt<Proof>>, ts: Pair<TS<U, D>, TP>) {
    inner.addAll(p1s)
    tsTp.add(ts)
  }

  fun take(): Triple<Pdt<Proof>, TS<U, D>, TP> {
    val expl = inner.removeFirst()
    val (ts, tp) = tsTp.removeFirst()
    return Triple(expl, ts, tp)
  }

  override fun iterator(): Iterator<Triple<Pdt<Proof>, TS<U, D>, TP>> = BufTIterator(this)
}

class BufTIterator<U : TickUnit<U, D>, D : TickDifference<D>>(val self: BufT<U, D>) :
    Iterator<Triple<Pdt<Proof>, TS<U, D>, TP>> {
  override fun hasNext(): Boolean = self.inner.isNotEmpty() && self.tsTp.isNotEmpty()

  override fun next(): Triple<Pdt<Proof>, TS<U, D>, TP> = self.take()
}

class Buf2T<U : TickUnit<U, D>, D : TickDifference<D>>(
    val lhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val rhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val tsTp: MutableList<Pair<TS<U, D>, TP>> = mutableListOf()
) : Iterable<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS<U, D>, TP>> {
  fun add(p1s: List<Pdt<Proof>>, p2s: List<Pdt<Proof>>, ts: Pair<TS<U, D>, TP>) {
    lhs.addAll(p1s)
    rhs.addAll(p2s)
    tsTp.add(ts)
  }

  fun take(): Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS<U, D>, TP> {
    val expl1 = lhs.removeFirst()
    val expl2 = rhs.removeFirst()
    val (ts, tp) = tsTp.removeFirst()
    return Triple(expl1 to expl2, ts, tp)
  }

  override fun iterator(): Iterator<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS<U, D>, TP>> =
      Buf2TIterator(this)
}

class Buf2TIterator<U : TickUnit<U, D>, D : TickDifference<D>>(val self: Buf2T<U, D>) :
    Iterator<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS<U, D>, TP>> {
  override fun hasNext(): Boolean =
      self.lhs.isNotEmpty() && self.rhs.isNotEmpty() && self.tsTp.isNotEmpty()

  override fun next(): Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS<U, D>, TP> = self.take()
}
