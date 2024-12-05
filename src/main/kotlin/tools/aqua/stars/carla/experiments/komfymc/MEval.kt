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

import tools.aqua.auxStructures.*
import tools.aqua.stars.carla.experiments.komfymc.MStates.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.*
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit
import tools.aqua.stars.data.av.dataclasses.TickDataDifferenceMilliseconds
import tools.aqua.stars.data.av.dataclasses.TickDataDifferenceSeconds

class MEval<U : TickUnit<U, D>, D : TickDifference<D>>(var maxTick: TS<U, D>? = null) {

  fun init(formula: Formula): MState<U, D> {
    return when (formula) {
      is TT -> MTT()
      is FF -> MFF()
      is Neg -> MNegate(init(formula.inner))
      is And -> MAnd(init(formula.lhs), init(formula.rhs))
      is Or -> MOr(init(formula.lhs), init(formula.rhs))
      is Iff -> MIff(init(formula.lhs), init(formula.rhs))
      is Implication -> MImpl(init(formula.lhs), init(formula.rhs))
      is Prev<*> -> MPrev<U, D>(getInterval(formula.interval as Pair<D, D?>?), init(formula.inner))
      is Next<*> -> MNext<U, D>(getInterval(formula.interval as Pair<D, D?>?), init(formula.inner), endTS = maxTick)
      is Once<*> -> MOnce<U, D>(getInterval(formula.interval as Pair<D, D?>?), init(formula.inner))
      is Historically<*> ->
          MHistorically<U, D>(getInterval(formula.interval as Pair<D, D?>?), init(formula.inner))
      is Eventually<*> ->
          MEventually<U, D>(
              getInterval(formula.interval as Pair<D, D?>?),
              init(formula.inner),
              eAux = Leaf(Eaux<U, D>(maxTick)))
      is Always<*> ->
          MAlways<U, D>(
              getInterval(formula.interval as Pair<D, D?>?),
              init(formula.inner),
              aAux = Leaf(Aaux<U, D>(endTS = maxTick)))
      is Since<*> ->
          MSince<U, D>(
              getInterval(formula.interval as Pair<D, D?>?), init(formula.lhs), init(formula.rhs))
      is Until<*> ->
          MUntil<U, D>(
              getInterval(formula.interval as Pair<D, D?>?),
              init(formula.lhs),
              init(formula.rhs),
              uaux = Leaf(Uaux<U, D>(endTS = maxTick)))
      is UnaryPredicate<*> ->
          MUnaryPred(formula.ref, formula.phi as ((EntityType<*, *, *, *, *>) -> Boolean))
      is BinaryPredicate<*, *> ->
          MBinaryPred(
              formula.ref1,
              formula.ref2,
              formula.phi as ((EntityType<*, *, *, *, *>, EntityType<*, *, *, *, *>) -> Boolean))
      is Exists -> MExists(formula.ref, init(formula.inner))
      is Forall -> MForall(formula.ref, init(formula.inner))
      is MaxPrevalence<*> ->
          MMaxPrev<U, D>(
              getInterval(formula.interval as Pair<D, D?>?),
              formula.fraction,
              init(formula.inner),
              aux = Leaf(MaxAux<U, D>(endTS = maxTick)))
      is MinPrevalence<*> ->
          MMinPrev<U, D>(
              getInterval(formula.interval as Pair<D, D?>?),
              formula.fraction,
              init(formula.inner),
              aux = Leaf(MinAux<U, D>(endTS = maxTick)))
      is PastMaxPrevalence<*> ->
          MPastMaxPrev<U, D>(
              getInterval(formula.interval as Pair<D, D?>?), formula.fraction, init(formula.inner))
      is PastMinPrevalence<*> ->
          MPastMinPrev<U, D>(
              getInterval(formula.interval as Pair<D, D?>?), formula.fraction, init(formula.inner))
      is Binding<*> -> MBinding(formula.bindVariable, init(formula.inner))
      is UnBindPred<*, *> -> MUnBindPred(formula.ref, formula.bindVariable as (Bind<EntityType<*, *, *, *, *>, *>), formula.phi as ((EntityType<*, *, *, *, *>, Any) -> Boolean))
      is Eq<*> -> TODO()
      is Geq<*> -> TODO()
      is Gt<*> -> TODO()
      is Leq<*> -> TODO()
      is Lt<*> -> TODO()
      is Ne<*> -> TODO()
    }
  }

  private fun getInterval(pair: Pair<D, D?>?): RelativeInterval<D> {
    return if (pair == null) {
      RelativeInterval(null, null)
    } else {
        val start = pair.first
      if ((start is TickDataDifferenceSeconds && start.differenceSeconds == 0.0) ||
          (start is TickDataDifferenceMilliseconds && start.differenceMillis == 0L)
          ) {
          RelativeInterval(null, pair.second)
      } else {
          RelativeInterval(pair.first, pair.second)
      }
    }
  }
}
