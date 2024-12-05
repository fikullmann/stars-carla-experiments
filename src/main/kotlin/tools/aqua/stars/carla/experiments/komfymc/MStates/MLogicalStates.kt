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

import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

data class MNegate<U : TickUnit<U, D>, D : TickDifference<D>>(val inner: MState<U, D>) :
    MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val expls = inner.eval(ts, tp, ref)
    val fExpls = expls.map { expl -> expl.apply1(ref, ::evalNeg) }
    return fExpls
  }
}

data class MAnd<U : TickUnit<U, D>, D : TickDifference<D>>(
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf: Buf2 = Buf2()
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = lhs.eval(ts, tp, ref)
    val p2 = rhs.eval(ts, tp, ref)
    formula.buf.add(p1, p2)
    val fExpls = formula.buf.take(ref, ::evalAnd)
    return fExpls
  }
}

data class MOr<U : TickUnit<U, D>, D : TickDifference<D>>(
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf: Buf2 = Buf2()
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = lhs.eval(ts, tp, ref)
    val p2 = rhs.eval(ts, tp, ref)
    formula.buf.add(p1, p2)
    val fExpls = formula.buf.take(ref, ::evalOr)
    return fExpls
  }
}

data class MIff<U : TickUnit<U, D>, D : TickDifference<D>>(
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf: Buf2 = Buf2()
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = lhs.eval(ts, tp, ref)
    val p2 = rhs.eval(ts, tp, ref)
    formula.buf.add(p1, p2)
    val fExpls = formula.buf.take(ref, ::evalIff)
    return fExpls
  }
}

data class MImpl<U : TickUnit<U, D>, D : TickDifference<D>>(
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf: Buf2 = Buf2()
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = lhs.eval(ts, tp, ref)
    val p2 = rhs.eval(ts, tp, ref)
    formula.buf.add(p1, p2)
    val fExpls = formula.buf.take(ref, ::evalImpl)
    return fExpls
  }
}

fun evalNeg(p1: Proof): Proof {
  return when {
    p1 is SatProof -> VNeg(p1)
    p1 is ViolationProof -> SatNeg(p1)
    else -> ErrorProof
  }
}

fun evalAnd(p1: Proof, p2: Proof): Proof {
  return when {
    p1 is SatProof && p2 is SatProof -> SatAnd(p1, p2)
    p1 is SatProof && p2 is ViolationProof -> VAndR(p2)
    p1 is ViolationProof && p2 is SatProof -> VAndL(p1)
    p1 is ViolationProof && p2 is ViolationProof ->
        if (p1.size() <= p2.size()) VAndL(p1) else VAndR(p2)
    else -> ErrorProof
  }
}

fun evalOr(p1: Proof, p2: Proof): Proof {
  return when {
    p1 is SatProof && p2 is SatProof -> if (p1.size() <= p2.size()) SatOrL(p1) else SatOrR(p2)
    p1 is SatProof && p2 is ViolationProof -> SatOrL(p1)
    p1 is ViolationProof && p2 is SatProof -> SatOrR(p2)
    p1 is ViolationProof && p2 is ViolationProof -> VOr(p1, p2)
    else -> ErrorProof
  }
}

fun evalIff(p1: Proof, p2: Proof): Proof {
  return when {
    p1 is SatProof && p2 is SatProof -> SatIffSS(p1, p2)
    p1 is SatProof && p2 is ViolationProof -> VIffSV(p1, p2)
    p1 is ViolationProof && p2 is SatProof -> VIffVS(p1, p2)
    p1 is ViolationProof && p2 is ViolationProof -> SatIffVV(p1, p2)
    else -> ErrorProof
  }
}

fun evalImpl(p1: Proof, p2: Proof): Proof {
  return when {
    p1 is SatProof && p2 is SatProof -> SatImplR(p2)
    p1 is SatProof && p2 is ViolationProof -> VImpl(p1, p2)
    p1 is ViolationProof && p2 is SatProof ->
        if (p1.size() < p2.size()) SatImplL(p1) else SatImplR(p2)
    p1 is ViolationProof && p2 is ViolationProof -> SatImplL(p1)
    else -> ErrorProof
  }
}
