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

package tools.aqua.dsl

import tools.aqua.stars.carla.experiments.komfymc.dsl.Bind
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.core.types.*

sealed interface Formula

data object TT : Formula

data object FF : Formula

data class UnaryPredicate<E1 : EntityType<*, *, *>>(val ref: Ref<E1>, val phi: (E1) -> Boolean) :
    Formula

data class BinaryPredicate<E1 : EntityType<*, *, *>, E2 : EntityType<*, *, *>>(
    val ref1: Ref<E1>,
    val ref2: Ref<E2>,
    val phi: (E1, E2) -> Boolean
) : Formula

data class Neg(val inner: Formula) : Formula

data class And(val lhs: Formula, val rhs: Formula) : Formula

data class Or(val lhs: Formula, val rhs: Formula) : Formula

data class Implication(val lhs: Formula, val rhs: Formula) : Formula

data class Iff(val lhs: Formula, val rhs: Formula) : Formula

data class Prev(val interval: Pair<Double, Double>? = null, val inner: Formula) : Formula

data class Next(val interval: Pair<Double, Double>? = null, val inner: Formula) : Formula

data class Once(val interval: Pair<Double, Double>? = null, val inner: Formula) : Formula

data class Historically(val interval: Pair<Double, Double>? = null, val inner: Formula) : Formula

data class Eventually(val interval: Pair<Double, Double?>? = null, val inner: Formula) : Formula

data class Always(val interval: Pair<Double, Double>? = null, val inner: Formula) : Formula

data class Since(val interval: Pair<Double, Double>? = null, val lhs: Formula, val rhs: Formula) :
    Formula

data class Until(val interval: Pair<Double, Double>? = null, val lhs: Formula, val rhs: Formula) :
    Formula

data class Forall<E : EntityType<*, *, *>>(val ref: Ref<E>, val inner: Formula) : Formula

data class Exists<E : EntityType<*, *, *>>(val ref: Ref<E>, val inner: Formula) : Formula

data class MinPrevalence(
    val interval: Pair<Double, Double>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class PastMinPrevalence(
    val interval: Pair<Double, Double>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class MaxPrevalence(
    val interval: Pair<Double, Double>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class PastMaxPrevalence(
    val interval: Pair<Double, Double>? = null,
    val fraction: Double,
    val inner: Formula
) : Formula

data class Binding<E1 : EntityType<*, *, *>, Type : Any>(
    val bindVariable: Bind<E1, Type>,
    val inner: Formula
) : Formula

sealed interface Term<Type>

data class Constant<Type>(val value: Type) : Term<Type>

data class Variable<Type>(val phi: () -> Type) : Term<Type>

data class UnaryVariable<E1 : EntityType<*, *, *>, Type>(val ref: Ref<E1>, val phi: (E1) -> Type) :
    Term<Type>
