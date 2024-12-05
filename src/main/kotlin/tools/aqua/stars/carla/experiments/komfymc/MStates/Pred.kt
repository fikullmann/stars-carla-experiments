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
import tools.aqua.stars.carla.experiments.komfymc.dsl.Bind
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.RefId
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

interface Pred {
  fun call(): Boolean
}

data class MUnaryPred<U : TickUnit<U, D>, D : TickDifference<D>, T : EntityType<*, *, *, *, *>>(
    val ref: Ref<T>,
    val phi: (T) -> Boolean
) : Pred, MState<U, D> {
  override fun call() = phi.invoke(ref.now())

  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    if (formula.ref.fixed) {
      return listOf(Leaf(if (formula.call()) SatPred(tp) else VPred(tp)))
    } else {
      val part = formula.ref.allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc, e ->
                acc[RefId(e.id)] = Leaf(if (phi(e)) SatPred(tp) else VPred(tp))
                acc
            }
      return listOf(Node(formula.ref, part))
    }
  }
}

data class MBinaryPred<
    U : TickUnit<U, D>,
    D : TickDifference<D>,
    E1 : EntityType<*, *, *, *, *>,
    E2 : EntityType<*, *, *, *, *>>(
    val ref1: Ref<E1>,
    val ref2: Ref<E2>,
    inline val phi: (E1, E2) -> Boolean
) : Pred, MState<U, D> {
  fun fix1() = MUnaryPred<U, D, E2>(ref2, partial2First(phi, ref1.now()))

  fun fix2() = MUnaryPred<U, D, E1>(ref1, partial2Second(phi, ref2.now()))

  override fun call() = phi.invoke(ref1.now(), ref2.now())

  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
    val formula = this
    val pair = formula.ref1.fixed to formula.ref2.fixed
    when (pair) {
      true to true -> return listOf(Leaf(if (formula.call()) SatPred(tp) else VPred(tp)))
      true to false -> {
          val part = formula.ref2.allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc, e ->
              acc[RefId(e.id)] = Leaf(if (phi(ref1.now(), e)) SatPred(tp) else VPred(tp))
              acc
          }
          return listOf(Node(formula.ref2, part))
      }
      false to true -> return formula.fix2().eval(ts, tp, ref)
      else -> {
          val order = ref.indexOf(ref1 as Ref<*>) < ref.indexOf(ref2 as Ref<*>)
          if (order) {
              val part1 = ref1.allAtTick().associate { e1 ->
                  val part2 = ref2.allAtTick().associate { e2 ->
                      RefId(e2.id) to Leaf(if (formula.phi(e1, e2)) SatPred(tp) else VPred(tp))
                  }
                  RefId(e1.id) to Node(ref2, part2)
              }
              return listOf(Node(ref1, part1))
          } else {
              val part = ref2.allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc1, e2 ->
                  val part2 = ref1.allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc2, e1 ->
                      acc2[RefId(e1.id)] = Leaf(if (formula.phi(e1, e2)) SatPred(tp) else VPred(tp))
                      acc2
                  }
                  acc1[RefId(e2.id)] = Node(ref2, part2)
                  acc1
              }
              return listOf(Node(ref1, part))
          }
      }
    }
  }
}

data class MUnBindPred<
        U : TickUnit<U, D>,
        D : TickDifference<D>,
        E1 : EntityType<*, *, *, *, *>,
        X: Any>(
            val ref: Ref<E1>,
            val bind: Bind<EntityType<*, *, *, *, *>, X>,
            val phi: (E1, X) -> Boolean
): Pred, MState<U, D> {
    override fun call(): Boolean {
        TODO("Not yet implemented")
    }

    override fun eval(ts: TS<U, D>, tp: TP, ref: MutableList<Ref<EntityType<*, *, *, *, *>>>): List<Pdt<Proof>> {
        TODO("Not yet implemented")
    }

}