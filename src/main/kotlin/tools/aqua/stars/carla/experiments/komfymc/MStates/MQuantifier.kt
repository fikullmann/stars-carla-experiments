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
import tools.aqua.stars.carla.experiments.komfymc.dsl.RefId
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.TickDifference
import tools.aqua.stars.core.types.TickUnit

data class MExists<U : TickUnit<U, D>, D : TickDifference<D>>(
    val exRef: Ref<*>,
    val inner: MState<U, D>,
    // val proofs: MutableList<Tree> = mutableListOf(),
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
      if (exRef.allAtTick().isEmpty()) {
          return listOf(Leaf(VExistsNone(exRef, tp)))
      } else {
          val newVar = exRef as Ref<EntityType<*, *, *, *, *>>
          ref.add(newVar)
          val expls1 = inner.eval(ts, tp, ref)
          val fExpls = expls1.map { expl -> hide(ref, expl, ::existsLeaf, ::existsNode, newVar) }
          ref.removeLast()
          return fExpls
      }
  }
}

data class MForall<U : TickUnit<U, D>, D : TickDifference<D>>(
    val allRef: Ref<*>,
    val inner: MState<U, D>,
    // val proofs: MutableList<Proof> = mutableListOf(),
    // val tsTp: MutableList<Pair<TS, TP>> = mutableListOf()
) : MState<U, D> {
  override fun eval(
      ts: TS<U, D>,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *, *, *>>>
  ): List<Pdt<Proof>> {
      if (allRef.allAtTick().isEmpty()) {
          return listOf(Leaf(VExistsNone(allRef, tp)))
      } else {
          val newVar = allRef as Ref<EntityType<*, *, *, *, *>>
          ref.add(newVar)
          val expls1 = inner.eval(ts, tp, ref)
          val fExpls = expls1.map { expl -> hide(ref, expl, ::forallLeaf, ::forallNode, newVar) }
          return fExpls
      }
  }
}

fun <T : EntityType<*, *, *, *, *>> existsLeaf(ref: Ref<T>, p1: Proof): Proof {
  return when (p1) {
    is SatProof -> SatExists(ref, null, p1)
    is ViolationProof ->
        VExists(ref, listOf(RefId(ref.allAtTick().map { it.id }.firstOrNull() ?: -1) to p1))
    else -> ErrorProof
  }
}

fun <T : EntityType<*, *, *, *, *>> existsNode(ref: Ref<T>, part: Map<RefId, Proof>): Proof {
  if (part.any { it.value is SatProof }) {
    val sats = part.filter { it.value is SatProof }
    return minpList(
        sats.map { (refId, proof) ->
          assert(proof is SatProof)
          SatExists(ref, refId, proof as SatProof)
        })
  } else {
    return VExists(ref, part.map { (set, proof) -> set to (proof as ViolationProof) })
  }
}

fun <T : EntityType<*, *, *, *, *>> forallLeaf(ref: Ref<T>, p1: Proof): Proof {
  return when (p1) {
    is SatProof ->
        SatForall(ref, listOf(RefId(ref.allAtTick().map { it.id }.firstOrNull() ?: -1) to p1))
    is ViolationProof -> VForall(ref, null, p1)
    else -> ErrorProof
  }
}

fun <T : EntityType<*, *, *, *, *>> forallNode(ref: Ref<T>, part: Map<RefId, Proof>): Proof {
  if (part.all { it.value is SatProof }) {
    return SatForall(ref, part.map { (set, proof) -> set to (proof as SatProof) })
  } else {
    val viols = part.filter { it.value is ViolationProof }
    return minpList(
        viols.map { (refId, proof) ->
          assert(proof is ViolationProof)
          VForall(ref, refId, proof as ViolationProof)
        })
  }
}
