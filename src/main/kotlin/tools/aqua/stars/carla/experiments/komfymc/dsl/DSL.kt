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

fun <A, B> partial(f: (A) -> B, a: A): () -> B = { f(a) }

fun <A, B, C> partial2(f: (A, B) -> C, a: A, b: B): () -> C = { f(a, b) }

class FormulaBuilder(val phi: MutableList<Formula> = mutableListOf()) {
  companion object {
    fun formula(init: FormulaBuilder.() -> Unit): Formula {
      val builder = FormulaBuilder()
      init.invoke(builder)
      return builder.phi[0]
    }

    fun <
        E1 : E,
        E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U : TickUnit<U, D>,
        D : TickDifference<D>> formula(
        init: FormulaBuilder.(Ref<E1>) -> Unit
    ): (Ref<E1>) -> FormulaBuilder {
      return { ref: Ref<E1> ->
        val builder = FormulaBuilder()
        init.invoke(builder, ref)
        builder
      }
    }

    fun <
        E1 : E,
        E2 : E,
        E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U : TickUnit<U, D>,
        D : TickDifference<D>> formula(
        init: FormulaBuilder.(Ref<E1>, Ref<E2>) -> Unit
    ): (Ref<E1>, Ref<E2>) -> FormulaBuilder {
      return { ref1: Ref<E1>, ref2: Ref<E2> ->
        FormulaBuilder().apply { init(ref1, ref2) }.let { this }
        val builder = FormulaBuilder()
        init.invoke(builder, ref1, ref2)
        builder
      }
    }
  }

  fun <
      E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> ((Ref<E1>) -> FormulaBuilder).holds(ref1: Ref<E1>): Formula =
      this(ref1).phi[0].also { phi.add(it) }

  fun <
      E1 : E,
      E2 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> ((Ref<E1>, Ref<E2>) -> FormulaBuilder).holds(
      ref1: Ref<E1>,
      ref2: Ref<E2>
  ): Formula = this(ref1, ref2).phi[0].also { phi.add(it) }

  private fun buildNeg(): Neg = assert(phi.size == 1).let { Neg(phi.first()) }

  private fun buildAnd(): And = assert(phi.size == 2).let { And(phi[0], phi[1]) }

  private fun buildOr(): Or = assert(phi.size == 2).let { Or(phi[0], phi[1]) }

  private fun buildImpl(): Implication = assert(phi.size == 2).let { Implication(phi[0], phi[1]) }

  private fun buildIff(): Iff = assert(phi.size == 2).let { Iff(phi[0], phi[1]) }

  private fun <D : TickDifference<D>> buildPrev(interval: Pair<D, D>?): Prev<D> {
    assert(phi.size == 1)
    return Prev(interval, phi.first())
  }

  private fun <D : TickDifference<D>> buildNext(interval: Pair<D, D>?): Next<D> {
    assert(phi.size == 1)
    return Next(interval, phi.first())
  }

  private fun <D : TickDifference<D>> buildOnce(interval: Pair<D, D>?): Once<D> {
    assert(phi.size == 1)
    return Once(interval, phi.first())
  }

  private fun <D : TickDifference<D>> buildHistorically(interval: Pair<D, D>?): Historically<D> {
    assert(phi.size == 1)
    return Historically(interval, phi.first())
  }

  private fun <D : TickDifference<D>> buildEventually(interval: Pair<D, D>?): Eventually<D> {
    assert(phi.size == 1)
    return Eventually(interval, phi.first())
  }

  private fun <D : TickDifference<D>> buildAlways(interval: Pair<D, D>? = null): Always<D> {
    assert(phi.size == 1)
    return Always(interval, inner = phi[0])
  }

  private fun <D : TickDifference<D>> buildSince(interval: Pair<D, D>? = null): Since<D> {
    assert(phi.size == 2)
    return Since(interval, lhs = phi[0], rhs = phi[1])
  }

  private fun <D : TickDifference<D>> buildUntil(interval: Pair<D, D>? = null): Until<D> {
    assert(phi.size == 2)
    return Until(interval, lhs = phi[0], rhs = phi[1])
  }

  fun <
      E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> buildForall(ref: Ref<E1>): Forall {
    assert(phi.size == 1)
    return Forall(ref, phi[0])
  }

  fun <
      E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> buildExists(ref: Ref<E1>): Exists {
    assert(phi.size == 1)
    return Exists(ref, phi[0])
  }

  private fun <D : TickDifference<D>> buildMinPrevalence(
      interval: Pair<D, D>?,
      fraction: Double
  ): MinPrevalence<D> {
    assert(phi.size == 1)
    return MinPrevalence(interval, fraction, phi[0])
  }

  private fun <D : TickDifference<D>> buildMaxPrevalence(
      interval: Pair<D, D>?,
      fraction: Double
  ): MaxPrevalence<D> {
    assert(phi.size == 1)
    return MaxPrevalence(interval, fraction, phi[0])
  }

  private fun <D : TickDifference<D>> buildPastMinPrevalence(
      interval: Pair<D, D>?,
      fraction: Double
  ): PastMinPrevalence<D> {
    assert(phi.size == 1)
    return PastMinPrevalence(interval, fraction, phi[0])
  }

  private fun <D : TickDifference<D>> buildPastMaxPrevalence(
      interval: Pair<D, D>?,
      fraction: Double
  ): PastMaxPrevalence<D> {
    assert(phi.size == 1)
    return PastMaxPrevalence(interval, fraction, phi[0])
  }

  fun <
      E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>,
      Type : Any> buildBinding(bind: Bind<E1, Type>): Binding<Type> {
    assert(phi.size == 1)
    return Binding(bind, phi[0])
  }

  fun FormulaBuilder.tt(): TT = TT.also { phi.add(it) }

  fun FormulaBuilder.ff(): FF = FF.also { phi.add(it) }

  fun <
          E1: E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> FormulaBuilder.pred(
      ref1: Ref<E1>,
      init: (E1) -> Boolean = { true }
  ): Formula = UnaryPredicate(ref1, init).also { phi.add(it) }
    fun <X: Any,
            E1 : E,
            E : EntityType<E, T, S, U, D>,
            T : TickDataType<E, T, S, U, D>,
            S : SegmentType<E, T, S, U, D>,
            U : TickUnit<U, D>,
            D : TickDifference<D>> FormulaBuilder.pred(
        first:  Ref<E1>,
        second: Bind<E, X>,
        init: (E1, X) -> Boolean = { _, _ -> true }
    ): Formula = UnBindPred(first, second, init)

  fun <
      E1 : E,
      E2 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> FormulaBuilder.pred(
      ref1: Ref<E1>,
      ref2: Ref<E2>,
      init: (E1, E2) -> Boolean = { _, _ -> true }
  ): Formula = BinaryPredicate(ref1, ref2, init).also { phi.add(it) }

  fun FormulaBuilder.neg(input: Formula): Neg {
    return Neg(input).also { phi.add(it) }
  }

  fun FormulaBuilder.neg(init: FormulaBuilder.() -> Unit = {}): Neg {
    return FormulaBuilder().apply(init).buildNeg().also { phi.add(it) }
  }

  infix fun Formula.and(other: Formula): And =
      And(this, other).also {
        phi.removeLast()
        phi.removeLast()
        phi.add(it)
      }

  infix fun Formula.or(other: Formula): Or =
      Or(this, other).also {
        phi.removeLast()
        phi.removeLast()
        phi.add(it)
      }

  infix fun Formula.impl(other: Formula): Implication =
      Implication(this, other).also {
        phi.removeLast()
        phi.removeLast()
        phi.add(it)
      }

  infix fun Formula.iff(other: Formula): Iff =
      Iff(this, other).also {
        phi.removeLast()
        phi.removeLast()
        phi.add(it)
      }

  fun <D : TickDifference<D>> FormulaBuilder.prev(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Prev<D> {
    return FormulaBuilder().apply(init).buildPrev(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.next(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Next<D> {
    return FormulaBuilder().apply(init).buildNext(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.once(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Once<D> {
    return FormulaBuilder().apply(init).buildOnce(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.historically(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Historically<D> {
    return FormulaBuilder().apply(init).buildHistorically(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> eventually(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Eventually<D> {
    return FormulaBuilder().apply(init).buildEventually(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.globally(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Always<D> {
    return FormulaBuilder().apply(init).buildAlways(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.since(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Since<D> {
    return FormulaBuilder().apply(init).buildSince(interval).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.until(
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): Until<D> {
    return FormulaBuilder().apply(init).buildUntil(interval).also { phi.add(it) }
  }

  inline fun <
      reified E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> FormulaBuilder.forall(
      init: FormulaBuilder.(Ref<E1>) -> Unit = {}
  ): Forall {
    val ref = Ref<E1, E, T, S, U, D>()
    return FormulaBuilder().apply { init(ref) }.buildForall(ref).also { phi.add(it) }
  }

  inline fun <
      reified E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>> FormulaBuilder.exists(
      init: FormulaBuilder.(Ref<E1>) -> Unit = {}
  ): Exists {
    val ref = Ref<E1, E, T, S, U, D>()
    return FormulaBuilder().apply { init(ref) }.buildExists(ref).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.minPrevalence(
      fraction: Double,
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): MinPrevalence<D> {
    return FormulaBuilder().apply(init).buildMinPrevalence(interval, fraction).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.maxPrevalence(
      fraction: Double,
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): MaxPrevalence<D> {
    return FormulaBuilder().apply(init).buildMaxPrevalence(interval, fraction).also { phi.add(it) }
  }

  fun <D : TickDifference<D>> FormulaBuilder.pastMinPrevalence(
      fraction: Double,
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): PastMinPrevalence<D> {
    return FormulaBuilder().apply(init).buildPastMinPrevalence(interval, fraction).also {
      phi.add(it)
    }
  }

  fun <D : TickDifference<D>> FormulaBuilder.pastMaxPrevalence(
      fraction: Double,
      interval: Pair<D, D>? = null,
      init: FormulaBuilder.() -> Unit = {}
  ): PastMaxPrevalence<D> {
    return FormulaBuilder().apply(init).buildPastMaxPrevalence(interval, fraction).also {
      phi.add(it)
    }
  }

  inline fun <
      reified E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>,
      reified Type : Any> FormulaBuilder.binding(
      term: UnaryVariable<E1, Type>,
      init: FormulaBuilder.(Bind<E1, Type>) -> Unit = {}
  ): Binding<Type> {
    val bind = Bind(term.ref, term.phi)
    return FormulaBuilder().apply { init(bind) }.buildBinding(bind).also { phi.add(it) }
  }

  infix fun <Type> Term<Type>.leq(other: Term<Type>): Leq<Type> =
      Leq(this, other).also { phi.add(it) }

  infix fun <Type> Term<Type>.lt(other: Term<Type>): Lt<Type> = Lt(this, other).also { phi.add(it) }

  infix fun <Type> Term<Type>.geq(other: Term<Type>): Geq<Type> =
      Geq(this, other).also { phi.add(it) }

  infix fun <Type> Term<Type>.gt(other: Term<Type>): Gt<Type> = Gt(this, other).also { phi.add(it) }

  infix fun <Type> Term<Type>.eq(other: Term<Type>): Eq<Type> = Eq(this, other).also { phi.add(it) }

  infix fun <Type> Term<Type>.ne(other: Term<Type>): Ne<Type> = Ne(this, other).also { phi.add(it) }

  fun <Type> term(init: () -> Type): Variable<Type> = Variable(init)

  fun <Type> const(value: Type): Constant<Type> = Constant(value)

  fun <
      E1 : E,
      E : EntityType<E, T, S, U, D>,
      T : TickDataType<E, T, S, U, D>,
      S : SegmentType<E, T, S, U, D>,
      U : TickUnit<U, D>,
      D : TickDifference<D>,
      Type> term(ref1: Ref<E1>, init: (E1) -> Type): UnaryVariable<E1, Type> =
      UnaryVariable(ref1, init)
}

fun <
    E1 : E,
    E : EntityType<E, T, S, U, D>,
    T : TickDataType<E, T, S, U, D>,
    S : SegmentType<E, T, S, U, D>,
    U : TickUnit<U, D>,
    D : TickDifference<D>> ((Ref<E1>) -> FormulaBuilder).holds(ref1: Ref<E1>): Formula =
    this(ref1).phi[0]

fun <
    E1 : E,
    E2 : E,
    E : EntityType<E, T, S, U, D>,
    T : TickDataType<E, T, S, U, D>,
    S : SegmentType<E, T, S, U, D>,
    U : TickUnit<U, D>,
    D : TickDifference<D>> ((Ref<E1>, Ref<E2>) -> FormulaBuilder).holds(
    ref1: Ref<E1>,
    ref2: Ref<E2>
): Formula = this(ref1, ref2).phi[0]
