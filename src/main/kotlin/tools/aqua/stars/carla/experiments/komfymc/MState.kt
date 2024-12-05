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

package tools.aqua.stars.carla.experiments.komfymc
/*
import tools.aqua.auxStructures.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Bind
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType

sealed interface MState

data object MTT : MState

data object MFF : MState

interface Pred {
  fun call(): Boolean
}

data class MUnaryPred<T : EntityType<*, *, *>>(val ref: Ref<T>, val phi: (T) -> Boolean) :
    Pred, MState {
  override fun call() = phi.invoke(ref.now())
}

data class MBinaryPred<T1 : EntityType<*, *, *>, T2 : EntityType<*, *, *>>(
    val ref1: Ref<T1>,
    val ref2: Ref<T2>,
    inline val phi: (T1, T2) -> Boolean
) : Pred, MState {
  fun fix1() = MUnaryPred(ref2, partial2First(phi, ref1.now()))

  fun fix2() = MUnaryPred(ref1, partial2Second(phi, ref2.now()))

  override fun call() = phi.invoke(ref1.now(), ref2.now())
}

data class MNegate(val inner: MState) : MState

data class MAnd(val lhs: MState, val rhs: MState, val buf: Buf2 = Buf2()) : MState

data class MOr(val lhs: MState, val rhs: MState, val buf: Buf2 = Buf2()) : MState

data class MIff(val lhs: MState, val rhs: MState, val buf: Buf2 = Buf2()) : MState

data class MImpl(val lhs: MState, val rhs: MState, val buf: Buf2 = Buf2()) : MState

data class MPrev(
    val interval: Interval,
    val inner: MState,
    var first: Boolean = true,
    val buft: BufPrevNext = BufPrevNext(),
) : MState

data class MNext(
    val interval: Interval,
    val inner: MState,
    var first: Boolean = true,
    val buft: BufPrevNext = BufPrevNext(),
    val endTS: Double? = null,
) : MState

data class MOnce(
    val interval: Interval,
    val inner: MState,
    val buft: BufT = BufT(),
    var mAuxPdt: Pdt<Oaux> = Leaf(Oaux())
) : MState

data class MHistorically(
    val interval: Interval,
    val inner: MState,
    val buft: BufT = BufT(),
    var hAux: Pdt<Haux> = Leaf(Haux())
) : MState

data class MEventually(
    val interval: Interval,
    val inner: MState,
    val buft: BufT = BufT(),
    var eAux: Pdt<Eaux> = Leaf(Eaux())
) : MState

data class MAlways(
    val interval: Interval,
    val inner: MState,
    val buft: BufT = BufT(),
    var aAux: Pdt<Aaux> = Leaf(Aaux())
) : MState

data class MSince(
    val interval: Interval,
    val lhs: MState,
    val rhs: MState,
    val buf2t: Buf2T = Buf2T(),
    var saux: Pdt<Saux> = Leaf(Saux())
) : MState

data class MUntil(
    val interval: Interval,
    val lhs: MState,
    val rhs: MState,
    val buf2t: Buf2T = Buf2T(),
    var uaux: Pdt<Uaux> = Leaf(Uaux())
) : MState

data class MExists(
    val ref: Ref<*>,
    val inner: MState,
    // val proofs: MutableList<Tree> = mutableListOf(),
) : MState

data class MForall(
    val ref: Ref<*>,
    val inner: MState,
    // val proofs: MutableList<Proof> = mutableListOf(),
    // val tsTp: MutableList<Pair<TS, TP>> = mutableListOf()
) : MState

data class MPastMinPrev(
    val interval: Interval,
    val factor: Double,
    val inner: MState,
    val buft: BufT = BufT(),
    var aux: Pdt<PastMinAux> = Leaf(PastMinAux())
) : MState

data class MPastMaxPrev(
    val interval: Interval,
    val factor: Double,
    val inner: MState,
    val buft: BufT = BufT(),
    var aux: Pdt<PastMaxAux> = Leaf(PastMaxAux())
) : MState

data class MMinPrev(
    val interval: Interval,
    val factor: Double,
    val inner: MState,
    val buft: BufT = BufT(),
    var aux: Pdt<MinAux> = Leaf(MinAux())
) : MState

data class MMaxPrev(
    val interval: Interval,
    val factor: Double,
    val inner: MState,
    val buft: BufT = BufT(),
    var aux: Pdt<MaxAux> = Leaf(MaxAux())
) : MState

data class MBinding<E1 : EntityType<*, *, *>, T : Any>(
    val bindVariable: Bind<E1, T>,
    val inner: MState,
) : MState

class Buf2(
    val lhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val rhs: MutableList<Pdt<Proof>> = mutableListOf()
) {
  fun add(p1s: List<Pdt<Proof>>, p2s: List<Pdt<Proof>>) {
    lhs.addAll(p1s)
    rhs.addAll(p2s)
  }

  fun take(
      ref: MutableList<Ref<EntityType<*, *, *>>>,
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

class BufPrevNext(
    val inner: MutableList<Pdt<Proof>> = mutableListOf(),
    val tss: MutableList<TS> = mutableListOf()
) {
  fun clearInner() {
    inner.clear()
  }

  fun add(p1s: List<Pdt<Proof>>, ts: TS) {
    inner.addAll(p1s)
    tss.add(ts)
  }

  fun take(
      ref: MutableList<Ref<EntityType<*, *, *>>>,
      prev: Boolean,
      interval: Interval
  ): List<Pdt<Proof>> {
    val result = mutableListOf<Pdt<Proof>>()
    while (inner.isNotEmpty() && tss.size > 1) {
      val expl = inner.removeFirst()
      val ts1 = tss.removeFirst()
      val ts2 = tss.first()
      result.add(expl.apply1(ref) { p -> MPrevNext.update(prev, interval, p, ts1, ts2) })
    }
    return result
  }
}

class BufT(
    val inner: MutableList<Pdt<Proof>> = mutableListOf(),
    val tsTp: MutableList<Pair<TS, TP>> = mutableListOf()
) : Iterable<Triple<Pdt<Proof>, TS, TP>> {
  fun clearInner() {
    inner.clear()
  }

  fun add(p1s: List<Pdt<Proof>>, ts: Pair<TS, TP>) {
    inner.addAll(p1s)
    tsTp.add(ts)
  }

  fun take(): Triple<Pdt<Proof>, TS, TP> {
    val expl = inner.removeFirst()
    val (ts, tp) = tsTp.removeFirst()
    return Triple(expl, ts, tp)
  }

  override fun iterator(): Iterator<Triple<Pdt<Proof>, TS, TP>> = BufTIterator(this)
}

class BufTIterator(val self: BufT) : Iterator<Triple<Pdt<Proof>, TS, TP>> {
  override fun hasNext(): Boolean = self.inner.isNotEmpty() && self.tsTp.isNotEmpty()

  override fun next(): Triple<Pdt<Proof>, TS, TP> = self.take()
}

class Buf2T(
    val lhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val rhs: MutableList<Pdt<Proof>> = mutableListOf(),
    val tsTp: MutableList<Pair<TS, TP>> = mutableListOf()
) : Iterable<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS, TP>> {
  fun add(p1s: List<Pdt<Proof>>, p2s: List<Pdt<Proof>>, tstp: Pair<TS, TP>) {
    lhs.addAll(p1s)
    rhs.addAll(p2s)
    tsTp.add(tstp)
  }

  fun take(): Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS, TP> {
    val expl1 = lhs.removeFirst()
    val expl2 = rhs.removeFirst()
    val (ts, tp) = tsTp.removeFirst()
    return Triple(expl1 to expl2, ts, tp)
  }

  override fun iterator(): Iterator<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS, TP>> =
      Buf2TIterator(this)
}

class Buf2TIterator(val self: Buf2T) : Iterator<Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS, TP>> {
  override fun hasNext(): Boolean =
      self.lhs.isNotEmpty() && self.rhs.isNotEmpty() && self.tsTp.isNotEmpty()

  override fun next(): Triple<Pair<Pdt<Proof>, Pdt<Proof>>, TS, TP> = self.take()
}


 */
