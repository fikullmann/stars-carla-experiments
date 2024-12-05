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

import tools.aqua.auxStructures.Saux
import tools.aqua.auxStructures.Uaux
import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

data class MSince<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf2t: Buf2T<U, D> = Buf2T(),
    var saux: Pdt<Saux<U, D>> = Leaf(Saux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val pL = lhs.eval(ts, tp, ref)
    val pR = rhs.eval(ts, tp, ref)
    formula.buf2t.add(pL, pR, ts to tp)
    val result = mutableListOf<Pdt<Proof>>()
    for ((expl, ts1, tp1) in formula.buf2t) {
      val (expl1, expl2) = expl
      val (pPdt, auxPdt) =
          split(
              apply3(ref, expl1, expl2, formula.saux) { p1, p2, aux ->
                aux.update1(formula.interval, ts1, tp1, p1, p2)
              })
      result.add(pPdt)
      formula.saux = auxPdt
    }
    return result
  }
}

data class MUntil<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val lhs: MState<U, D>,
    val rhs: MState<U, D>,
    val buf2t: Buf2T<U, D> = Buf2T(),
    var uaux: Pdt<Uaux<U, D>> = Leaf(Uaux<U, D>())
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
      val pL = lhs.eval(ts, tp, ref)
      val pR = rhs.eval(ts, tp, ref)
      buf2t.add(pL, pR, ts to tp)
      val result = mutableListOf<Pdt<Proof>>()
      for ((expl, ts1, tp1) in buf2t) {
          val (expl1, expl2) = expl
          val (pPdt, auxPdt) = split(apply3(ref, expl1, expl2, uaux) { p1, p2, aux ->
              aux.update1(interval, ts1, tp1, p1, p2)
          })
          result.addAll(splitList(pPdt))
          uaux = auxPdt
      }
    return result
  }
}
