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

import tools.aqua.auxStructures.MaxAux
import tools.aqua.auxStructures.MinAux
import tools.aqua.auxStructures.PastMaxAux
import tools.aqua.auxStructures.PastMinAux
import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

data class MPastMinPrev<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val factor: Double,
    val inner: MState<U, D>,
    val buft: BufT<U, D> = BufT<U, D>(),
    var aux: Pdt<PastMinAux<U, D>> = Leaf(PastMinAux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = inner.eval(ts, tp, ref)
    formula.buft.add(p1, ts to tp)

    val result = mutableListOf<Pdt<Proof>>()
    for ((expl, ts1, tp1) in formula.buft) {
      val (pPdt, auxPdt) =
          split(
              apply2(ref, expl, formula.aux) { p, aux ->
                aux.update1(formula.interval, ts1, tp1, formula.factor, p)
              })
      result.add(pPdt)
      formula.aux = auxPdt
    }
    return result
  }
}

data class MPastMaxPrev<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val factor: Double,
    val inner: MState<U, D>,
    val buft: BufT<U, D> = BufT<U, D>(),
    var aux: Pdt<PastMaxAux<U, D>> = Leaf(PastMaxAux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = inner.eval(ts, tp, ref)
    formula.buft.add(p1, ts to tp)

    val result = mutableListOf<Pdt<Proof>>()
    for ((expl, ts1, tp1) in formula.buft) {
      val (pPdt, auxPdt) =
          split(
              apply2(ref, expl, formula.aux) { p, aux ->
                aux.update1(formula.interval, ts1, tp1, formula.factor, p)
              })
      result.add(pPdt)
      formula.aux = auxPdt
    }
    return result
  }
}

data class MMinPrev<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val factor: Double,
    val inner: MState<U, D>,
    val buft: BufT<U, D> = BufT<U, D>(),
    var aux: Pdt<MinAux<U, D>> = Leaf(MinAux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>,
  ): List<Pdt<Proof>> {
    val p1 = inner.eval(ts, tp, ref)
    buft.add(p1, ts to tp)
    val result = mutableListOf<Pdt<Proof>>()
    for ((expl, ts1, tp1) in buft) {
      val (listsPdt, auxPdt) =
          split(apply2(ref, expl, aux) { p, aux -> aux.update(interval, ts1, tp1, factor, p) })
      result.addAll(splitList(listsPdt))
      aux = auxPdt
    }
    return result
  }
}

data class MMaxPrev<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val factor: Double,
    val inner: MState<U, D>,
    val buft: BufT<U, D> = BufT<U, D>(),
    var aux: Pdt<MaxAux<U, D>> = Leaf(MaxAux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val p1 = inner.eval(ts, tp, ref)
    formula.buft.add(p1, ts to tp)
    val result = mutableListOf<Pdt<Proof>>()
    for ((expl, ts1, tp1) in formula.buft) {
      val (listsPdt, auxPdt) =
          split(
              apply2(ref, expl, formula.aux) { p, aux ->
                aux.update(formula.interval, ts1, tp1, formula.factor, p)
              })
      result.addAll(splitList(listsPdt))
      formula.aux = auxPdt
    }
    return result
  }
}
