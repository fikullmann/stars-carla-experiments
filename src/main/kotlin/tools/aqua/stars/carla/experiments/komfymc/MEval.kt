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
import tools.aqua.dsl.*
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.RefId
import tools.aqua.stars.core.types.EntityType

class MEval {
  companion object {
    var maxTick: Double? = null
  }

  fun init(formula: Formula): MState {
    return when (formula) {
      is TT -> MTT
      is FF -> MFF
      is Neg -> MNegate(init(formula.inner))
      is And -> MAnd(init(formula.lhs), init(formula.rhs))
      is Or -> MOr(init(formula.lhs), init(formula.rhs))
      is Iff -> MIff(init(formula.lhs), init(formula.rhs))
      is Implication -> MImpl(init(formula.lhs), init(formula.rhs))
      is Prev -> MPrev(getInterval(formula.interval), init(formula.inner))
      is Next -> MNext(getInterval(formula.interval), init(formula.inner), endTS = maxTick)
      is Once -> MOnce(getInterval(formula.interval), init(formula.inner))
      is Historically -> MHistorically(getInterval(formula.interval), init(formula.inner))
      is Eventually ->
          MEventually(
              getInterval(formula.interval),
              init(formula.inner),
              eAux = Leaf(Eaux(endTS = maxTick)))
      is Always ->
          MAlways(
              getInterval(formula.interval),
              init(formula.inner),
              aAux = Leaf(Aaux(endTS = maxTick)))
      is Since -> MSince(getInterval(formula.interval), init(formula.lhs), init(formula.rhs))
      is Until ->
          MUntil(
              getInterval(formula.interval),
              init(formula.lhs),
              init(formula.rhs),
              uaux = Leaf(Uaux(endTS = maxTick)))
      is UnaryPredicate<*> ->
          MUnaryPred(formula.ref, formula.phi as ((EntityType<*, *, *>) -> Boolean))
      is BinaryPredicate<*, *> ->
          MBinaryPred(
              formula.ref1,
              formula.ref2,
              formula.phi as ((EntityType<*, *, *>, EntityType<*, *, *>) -> Boolean))
      is Binding<*, *> -> MBinding(formula.bindVariable, init(formula.inner))
      is Exists<*> -> MExists(formula.ref, init(formula.inner))
      is Forall<*> -> MForall(formula.ref, init(formula.inner))
      is MaxPrevalence ->
          MMaxPrev(
              getInterval(formula.interval),
              formula.fraction,
              init(formula.inner),
              aux = Leaf(MaxAux(endTS = maxTick)))
      is MinPrevalence ->
          MMinPrev(
              getInterval(formula.interval),
              formula.fraction,
              init(formula.inner),
              aux = Leaf(MinAux(endTS = maxTick)))
      is PastMaxPrevalence ->
          MPastMaxPrev(getInterval(formula.interval), formula.fraction, init(formula.inner))
      is PastMinPrevalence ->
          MPastMinPrev(getInterval(formula.interval), formula.fraction, init(formula.inner))
      is Binding<*, *> -> MBinding(formula.bindVariable, init(formula.inner))
      is Eq<*> -> TODO()
      is Geq<*> -> TODO()
      is Gt<*> -> TODO()
      is Leq<*> -> TODO()
      is Lt<*> -> TODO()
      is Ne<*> -> TODO()
    }
  }

  private fun getInterval(pair: Pair<Double, Double?>?): Interval {
    return if (pair == null) {
      InfInterval(0.0)
    } else {
      if (pair.second != null) {
        BoundedInterval(pair.first, pair.second!!)
      } else {
        InfInterval(pair.first)
      }
    }
  }

  fun eval(
      ts: TS,
      tp: TP,
      ref: MutableList<Ref<EntityType<*, *, *>>>,
      formula: MState
  ): List<Pdt<Proof>> {
    when (formula) {
      is MTT -> return listOf(Leaf<Proof>(SatTT(tp)))
      is MFF -> return listOf(Leaf<Proof>(VFF(tp)))
      is MUnaryPred<*> -> {
        if (formula.ref.fixed) {
          return listOf(Leaf(if (formula.call()) SatPred(tp) else VPred(tp)))
        } else {
          val part = formula.ref.cycleEntitiesAtTick(tp, formula.phi as (EntityType<*, *, *>) -> Boolean)
          // val node = Node(formula.ref, part)
          return listOf(Node(formula.ref, part))
        }
      }
      is MBinaryPred<*, *> -> {
        val pair = formula.ref1.fixed to formula.ref2.fixed
        when (pair) {
          true to true -> return listOf(
            Leaf(
              if (formula.call()) {
                SatPred(tp)
              } else { VPred(tp) })
          )
          true to false -> return eval(ts, tp, ref, formula.fix1())
          false to true -> return eval(ts, tp, ref, formula.fix2())
          else -> {
            val order = ref.indexOf(formula.ref1) < ref.indexOf(formula.ref2)
            val firstRef = if (order) formula.ref1 else formula.ref2
            val secondRef = if (order) formula.ref2 else formula.ref1
            val part = firstRef.cycleBinaryEntitiesAtTick(tp, formula.phi as (EntityType<*, *, *>, EntityType<*, *, *>) -> Boolean, secondRef)
            return listOf(Node(firstRef, part))
          }
        }
      }
      is MNegate -> {
        val expls = eval(ts, tp, ref, formula.inner)
        val fExpls = expls.map { expl -> expl.apply1(ref, ::evalNeg) }
        return fExpls
      }
      is MAnd -> {
        val p1 = eval(ts, tp, ref, formula.lhs)
        val p2 = eval(ts, tp, ref, formula.rhs)
        formula.buf.add(p1, p2)
        val fExpls = formula.buf.take(ref, ::evalAnd)
        return fExpls
      }
      is MOr -> {
        val p1 = eval(ts, tp, ref, formula.lhs)
        val p2 = eval(ts, tp, ref, formula.rhs)
        formula.buf.add(p1, p2)
        val fExpls = formula.buf.take(ref, ::evalOr)
        return fExpls
      }
      is MIff -> {
        val p1 = eval(ts, tp, ref, formula.lhs)
        val p2 = eval(ts, tp, ref, formula.rhs)
        formula.buf.add(p1, p2)
        val fExpls = formula.buf.take(ref, ::evalIff)
        return fExpls
      }
      is MImpl -> {
        val p1 = eval(ts, tp, ref, formula.lhs)
        val p2 = eval(ts, tp, ref, formula.rhs)
        formula.buf.add(p1, p2)
        val fExpls = formula.buf.take(ref, ::evalImpl)
        return fExpls
      }
      is MPrev -> {
        val expl = eval(ts, tp, ref, formula.inner)
        formula.buft.add(expl, ts)
        val prevvedExpl =
            formula.buft
                .take(
                    ref,
                    true,
                    formula.interval,
                )
                .toMutableList()
        if (formula.first) {
          prevvedExpl.add(0, Leaf(VPrev0))
          formula.first = false
        }
        return prevvedExpl
      }
      is MNext -> {
        val expl = eval(ts, tp, ref, formula.inner).toMutableList()
        if (formula.first && expl.isNotEmpty()) {
          expl.removeFirst()
          formula.first = false
        }
        formula.buft.add(expl, ts)
        val nextedExpl = formula.buft.take(ref, false, formula.interval).toMutableList()
        if (ts.i == formula.endTS) {
          nextedExpl.add(Leaf(VNextInf(tp)))
        }
        formula.buft.clearInner()
        return nextedExpl
      }
      is MOnce -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)

        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (pPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.mAuxPdt) { p, aux ->
                    aux.update1(formula.interval, ts1, tp1, p)
                  })
          result.add(pPdt)
          formula.mAuxPdt = auxPdt
        }
        formula.buft.clearInner()
        return result
      }
      is MHistorically -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)
        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (pPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.hAux) { p, aux ->
                    aux.update1(formula.interval, ts1, tp1, p)
                  })
          result.add(pPdt)
          formula.hAux = auxPdt
        }
        formula.buft.clearInner()
        return result
      }
      is MEventually -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)

        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (listsPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.eAux) { p, aux ->
                    aux.update(formula.interval, ts1, tp1, p)
                  })
          result.addAll(splitList(listsPdt))
          formula.eAux = auxPdt
        }
        return result
      }
      is MAlways -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)
        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (listsPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.aAux) { p, aux ->
                    aux.update(formula.interval, ts1, tp1, p)
                  })
          result.addAll(splitList(listsPdt))
          formula.aAux = auxPdt
        }
        return result
      }
      is MSince -> {
        val pL = eval(ts, tp, ref, formula.lhs)
        val pR = eval(ts, tp, ref, formula.rhs)
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
      is MUntil -> {
        val pL = eval(ts, tp, ref, formula.lhs)
        val pR = eval(ts, tp, ref, formula.rhs)
        formula.buf2t.add(pL, pR, ts to tp)
        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buf2t) {
          val (expl1, expl2) = expl
          val (pPdt, auxPdt) =
              split(
                  apply3(ref, expl1, expl2, formula.uaux) { p1, p2, aux ->
                    aux.update1(formula.interval, ts1, tp1, p1, p2)
                  })
          result.addAll(splitList(pPdt))
          formula.uaux = auxPdt
        }
        return result
      }
      is MExists -> {
        // formula.ref.tick = ts.i
        if (formula.ref.allAtTick().isEmpty()) {
          return listOf(Leaf(VExistsNone(formula.ref, tp)))
        } else {
          val newVar = formula.ref as Ref<EntityType<*, *, *>>
          ref.add(newVar)
          val expls1 = eval(ts, tp, ref, formula.inner)
          val fExpls = expls1.map { expl -> hide(ref, expl, ::existsLeaf, ::existsNode, newVar) }
          ref.removeLast()
          return fExpls
        }
      }
      is MForall -> {
        // formula.ref.tick = ts.i
        if (formula.ref.allAtTick().isEmpty()) {
          return listOf(Leaf(SatForallNone(formula.ref, tp)))
        } else {
          val newVar = formula.ref as Ref<EntityType<*, *, *>>
          ref.add(newVar)
          val expls1 = eval(ts, tp, ref, formula.inner)
          val fExpls = expls1.map { expl -> hide(ref, expl, ::forallLeaf, ::forallNode, newVar) }
          return fExpls
        }
      }
      is MPastMaxPrev -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)

        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (pPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.aux) { p, aux ->
                    aux.update1(formula.interval, ts1, tp1, formula.factor, p)
                  })
          result.add(pPdt)
          formula.aux = auxPdt
        }
        return result
      }
      is MPastMinPrev -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)

        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (pPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.aux) { p, aux ->
                    aux.update1(formula.interval, ts1, tp1, formula.factor, p)
                  })
          result.add(pPdt)
          formula.aux = auxPdt
        }
        return result
      }
      is MMaxPrev -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)
        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (listsPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.aux) { p, aux ->
                    aux.update(formula.interval, ts1, tp1, formula.factor, p)
                  })
          result.addAll(splitList(listsPdt))
          formula.aux = auxPdt
        }
        return result
      }
      is MMinPrev -> {
        val p1 = eval(ts, tp, ref, formula.inner)
        formula.buft.add(p1, ts to tp)
        val result = mutableListOf<Pdt<Proof>>()
        for ((expl, ts1, tp1) in formula.buft) {
          val (listsPdt, auxPdt) =
              split(
                  apply2(ref, expl, formula.aux) { p, aux ->
                    aux.update(formula.interval, ts1, tp1, formula.factor, p)
                  })
          result.addAll(splitList(listsPdt))
          formula.aux = auxPdt
        }
        return result
      }
      is MBinding<*, *> -> {
        if (tp.i == 0) {
          formula.bindVariable.calculate()
        }
        val p1 = eval(ts, tp, ref, formula.inner)
        return p1
      }
      else -> return listOf(Leaf<Proof>(ErrorProof))
    }
  }

  private fun evalNeg(p1: Proof): Proof {
    return when {
      p1 is SatProof -> VNeg(p1)
      p1 is ViolationProof -> SatNeg(p1)
      else -> ErrorProof
    }
  }

  fun evalAnd(p1: Proof, p2: Proof): Proof {
    return when {
      p1 is SatProof && p2 is SatProof -> SatAnd(p1, p2)
      p1 is SatProof && p2 is ViolationProof -> VAndR(p2)
      p1 is ViolationProof && p2 is SatProof -> VAndL(p1)
      p1 is ViolationProof && p2 is ViolationProof ->
          if (p1.size() <= p2.size()) VAndL(p1) else VAndR(p2)
      else -> ErrorProof
    }
  }

  fun evalOr(p1: Proof, p2: Proof): Proof {
    return when {
      p1 is SatProof && p2 is SatProof -> if (p1.size() <= p2.size()) SatOrL(p1) else SatOrR(p2)
      p1 is SatProof && p2 is ViolationProof -> SatOrL(p1)
      p1 is ViolationProof && p2 is SatProof -> SatOrR(p2)
      p1 is ViolationProof && p2 is ViolationProof -> VOr(p1, p2)
      else -> ErrorProof
    }
  }

  fun evalIff(p1: Proof, p2: Proof): Proof {
    return when {
      p1 is SatProof && p2 is SatProof -> SatIffSS(p1, p2)
      p1 is SatProof && p2 is ViolationProof -> VIffSV(p1, p2)
      p1 is ViolationProof && p2 is SatProof -> VIffVS(p1, p2)
      p1 is ViolationProof && p2 is ViolationProof -> SatIffVV(p1, p2)
      else -> ErrorProof
    }
  }

  fun evalImpl(p1: Proof, p2: Proof): Proof {
    return when {
      p1 is SatProof && p2 is SatProof -> SatImplR(p2)
      p1 is SatProof && p2 is ViolationProof -> VImpl(p1, p2)
      p1 is ViolationProof && p2 is SatProof ->
          if (p1.size() < p2.size()) SatImplL(p1) else SatImplR(p2)
      p1 is ViolationProof && p2 is ViolationProof -> SatImplL(p1)
      else -> ErrorProof
    }
  }

  private fun <T : EntityType<*, *, *>> existsLeaf(ref: Ref<T>, p1: Proof): Proof {
    return when (p1) {
      is SatProof -> SatExists(ref, null, p1)
      is ViolationProof ->
          VExists(ref, listOf(RefId(ref.allAtTick().map { it.id }.firstOrNull() ?: -1) to p1))
      else -> ErrorProof
    }
  }

  private fun <T : EntityType<*, *, *>> existsNode(
      ref: Ref<T>,
      part: List<Pair<RefId, Proof>>
  ): Proof {
    if (part.any { it.second is SatProof }) {
      val sats = part.filter { it.second is SatProof }
      return minpList(
          sats.map { (refId, proof) ->
            assert(proof is SatProof)
            SatExists(ref, refId, proof as SatProof)
          })
    } else {
      return VExists(ref, part.map { (set, proof) -> set to (proof as ViolationProof) })
    }
  }

  private fun <T : EntityType<*, *, *>> forallLeaf(ref: Ref<T>, p1: Proof): Proof {
    return when (p1) {
      is SatProof ->
          SatForall(ref, listOf(RefId(ref.allAtTick().map { it.id }.firstOrNull() ?: -1) to p1))
      is ViolationProof -> VForall(ref, null, p1)
      else -> ErrorProof
    }
  }

  private fun <T : EntityType<*, *, *>> forallNode(
      ref: Ref<T>,
      part: List<Pair<RefId, Proof>>
  ): Proof {
    if (part.all { it.second is SatProof }) {
      return SatForall(ref, part.map { (set, proof) -> set to (proof as SatProof) })
    } else {
      val viols = part.filter { it.second is ViolationProof }
      return minpList(
          viols.map { (refId, proof) ->
            assert(proof is ViolationProof)
            VForall(ref, refId, proof as ViolationProof)
          })
    }
  }
}
