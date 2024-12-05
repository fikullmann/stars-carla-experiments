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
import tools.aqua.stars.carla.experiments.komfymc.TS
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

data class MPrev<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val inner: MState<U, D>,
    var first: Boolean = true,
    val buft: BufPrevNext<U, D> = BufPrevNext(),
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val expl = inner.eval(ts, tp, ref)
    formula.buft.add(expl, ts)
    val prevvedExpl = formula.buft.take(ref,true, formula.interval).toMutableList()
    if (formula.first) {
      prevvedExpl.add(0, Leaf(VPrev0))
      formula.first = false
    }
    return prevvedExpl
  }
}

data class MNext<U : TickUnit<U, D>, D : TickDifference<D>>(
    val interval: RelativeInterval<D>,
    val inner: MState<U, D>,
    var first: Boolean = true,
    val buft: BufPrevNext<U, D> = BufPrevNext(),
    val endTS: TS<U, D>?
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val expl = inner.eval(ts, tp, ref).toMutableList()
    if (formula.first && expl.isNotEmpty()) {
      expl.removeFirst()
      formula.first = false
    }
    formula.buft.add(expl, ts)
    val nextedExpl = formula.buft.take(ref, false, formula.interval).toMutableList()
    formula.buft.clearInner()
    if (ts == endTS) { nextedExpl.add(Leaf(VNextInf(tp))) }
    return nextedExpl
  }
}
