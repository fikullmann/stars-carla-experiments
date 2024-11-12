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

@file:Suppress("unused")

package tools.aqua.stars.carla.experiments.komfymc.dsl

import tools.aqua.stars.core.types.*

class Bind<E1 : EntityType<*, *, *>, T : Any>(
    val ref: Ref<E1>,
    private val term: (E1) -> T,
    val binding: MutableMap<RefId, T> = mutableMapOf(),
) {

  fun with(entity: E1): T =
      binding[RefId(entity.id)] ?: throw Exception("The binding was not previously configured.")

  fun calculate() {
    ref.allAtTick().forEach { e -> binding[RefId(e.id)] = term(e) }
  }
}
