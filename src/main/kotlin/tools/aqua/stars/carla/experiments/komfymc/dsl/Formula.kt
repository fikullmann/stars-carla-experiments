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

package tools.aqua.stars.carla.experiments.komfymc.dsl

import tools.aqua.stars.core.types.*

sealed interface Formula

data object TT : Formula

data object FF : Formula

data class UnaryPredicate<E1 : EntityType<*, *, *, *, *>>(
    val ref: Ref<E1>,
    val phi: (E1) -> Boolean
) : Formula

data class BinaryPredicate<E1 : EntityType<*, *, *, *, *>, E2 : EntityType<*, *, *, *, *>>(
    val ref1: Ref<E1>,
    val ref2: Ref<E2>,
    val phi: (E1, E2) -> Boolean
) : Formula

data class UnBindPred<E1 : EntityType<*, *, *, *, *>, Type: Any>(
    val ref: Ref<E1>,
    val bindVariable: Bind<out EntityType<*, *, *, *, *>, Type>,
    val phi: (E1, Type) -> Boolean
) : Formula


data class Neg(val inner: Formula) : Formula

data class And(val lhs: Formula, val rhs: Formula) : Formula

data class Or(val lhs: Formula, val rhs: Formula) : Formula

data class Implication(val lhs: Formula, val rhs: Formula) : Formula

data class Iff(val lhs: Formula, val rhs: Formula) : Formula

data class Prev<D : TickDifference<D>>(val interval: Pair<D, D>? = null, val inner: Formula) :
    Formula

data class Next<D : TickDifference<D>>(val interval: Pair<D, D>? = null, val inner: Formula) :
    Formula

data class Once<D : TickDifference<D>>(val interval: Pair<D, D>? = null, val inner: Formula) :
    Formula

data class Historically<D : TickDifference<D>>(
    val interval: Pair<D, D>? = null,
    val inner: Formula
) : Formula

data class Eventually<D : TickDifference<D>>(val interval: Pair<D, D>? = null, val inner: Formula) :
    Formula

data class Always<D : TickDifference<D>>(val interval: Pair<D, D>? = null, val inner: Formula) :
    Formula

data class Since<D : TickDifference<D>>(
    val interval: Pair<D, D>? = null,
    val lhs: Formula,
    val rhs: Formula
) : Formula

data class Until<D : TickDifference<D>>(
    val interval: Pair<D, D>? = null,
    val lhs: Formula,
    val rhs: Formula
) : Formula

data class Forall(val ref: Ref<out EntityType<*, *, *, *, *>>, val inner: Formula) : Formula

data class Exists(val ref: Ref<out EntityType<*, *, *, *, *>>, val inner: Formula) : Formula

data class MinPrevalence<D : TickDifference<*>>(
    val interval: Pair<D, D>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class PastMinPrevalence<D : TickDifference<*>>(
    val interval: Pair<D, D>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class MaxPrevalence<D : TickDifference<*>>(
    val interval: Pair<D, D>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class PastMaxPrevalence<D : TickDifference<*>>(
    val interval: Pair<D, D>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class Binding<Type : Any>(
    val bindVariable: Bind<out EntityType<*, *, *, *, *>, Type>,
    val inner: Formula
) : Formula

sealed interface Term<Type>

data class Constant<Type>(val value: Type) : Term<Type>

data class Variable<Type>(val phi: () -> Type) : Term<Type>

data class UnaryVariable<E1 : EntityType<*, *, *, *, *>, Type>(
    val ref: Ref<E1>,
    val phi: (E1) -> Type
) : Term<Type>
