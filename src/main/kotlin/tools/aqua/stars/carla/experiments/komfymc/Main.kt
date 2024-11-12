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

import tools.aqua.dsl.Formula
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.*

fun <E : EntityType<E, T, S>, T : TickDataType<E, T, S>, S : SegmentType<E, T, S>> eval(
    entities: S,
    formula: Formula
): List<Proof> {
  val result = mutableMapOf<Int, Pdt<Proof>>()
  val mEval = MEval()
  MEval.maxTick = entities.ticks.keys.max()
  val state = mEval.init(formula)
  Ref.cycle(entities) { idx, t ->
    val p = mEval.eval(TS(t), TP(idx), mutableListOf(), state)
    p.forEach { result[at(it).i] = it }
  }
  return result.values.mapNotNull { if (it is Leaf) it.value else null }
}
