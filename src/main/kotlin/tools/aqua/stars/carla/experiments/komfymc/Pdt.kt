/*
 * Copyright 2023-2024 The STARS Carla Experiments Authors
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

import java.util.*
import tools.aqua.auxStructures.EmptyVariableReferences
import tools.aqua.auxStructures.IncorrectVariableReferences
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.RefId
import tools.aqua.stars.core.types.EntityType

abstract class Pdt<Aux> {
  abstract fun <Result> apply1(
      vars: MutableList<Ref<EntityType<*, *, *>>>,
      f: (Aux) -> Result
  ): Pdt<Result>
}

class Leaf<Aux>(val value: Aux) : Pdt<Aux>() {
  override fun <Result> apply1(
      vars: MutableList<Ref<EntityType<*, *, *>>>,
      f: (Aux) -> Result
  ): Pdt<Result> {
    return Leaf(f(value))
  }
}

class Node<Aux>(val variable: Ref<out EntityType<*, *, *>>, val partition: Map<RefId, Pdt<Aux>>) :
    Pdt<Aux>() {
  override fun <Result> apply1(
      vars: MutableList<Ref<EntityType<*, *, *>>>,
      f: (Aux) -> Result
  ): Pdt<Result> {
    if (vars.isNotEmpty()) {
      for (variable in vars) {
        if (this.variable == variable) {
          return Node(variable, partition.mapValues { (_, value) -> value.apply1(vars, f) })
        }
      }
      throw IncorrectVariableReferences()
    }
    throw EmptyVariableReferences()
  }
}

fun at(pdt: Pdt<Proof>): TP {
  return when (pdt) {
    is Leaf<Proof> -> pdt.value.at()
    is Node<Proof> -> at(pdt.partition.values.first())
    else -> throw Exception()
  }
}

fun <P, Aux> split(pdt: Pdt<Pair<P, Aux>>): Pair<Pdt<P>, Pdt<Aux>> {
  when (pdt) {
    is Leaf<Pair<P, Aux>> -> return Leaf(pdt.value.first) to Leaf(pdt.value.second)
    is Node<Pair<P, Aux>> -> {
      val proofPartition = mutableMapOf<RefId, Pdt<P>>()
      val auxPartition = mutableMapOf<RefId, Pdt<Aux>>()
      for ((key, value) in pdt.partition) {
        split(value).let { (proof, aux) ->
          proofPartition[key] = proof
          auxPartition[key] = aux
        }
      }
      return Node(pdt.variable, proofPartition) to Node(pdt.variable, auxPartition)
    }
    else -> throw Exception()
  }
}

fun <P> splitList(pdt: Pdt<MutableList<P>>): List<Pdt<P>> {
  when (pdt) {
    is Leaf -> return pdt.value.map { Leaf(it) }
    is Node -> {
      val childPartition = mutableListOf<List<Pdt<P>>>()
      val partSplitList = pdt.partition.mapValues { splitList(it.value) }
      val partFst = partSplitList.keys
      val partSnd = partSplitList.values

      for (i in partSnd.first().indices) {
        childPartition.add(partSnd.map { it[i] })
      }

      return childPartition.map { Node(pdt.variable, partFst.zip(it).toMap()) }
    }
    else -> throw Exception("Pdt can only be Node or Leaf, but somehow it was neither.")
  }
}

fun <P, Result> apply1(
    vars: MutableList<Ref<EntityType<*, *, *>>>,
    tree: Pdt<P>,
    f: (P) -> Result
): Pdt<Result> {
  if (tree is Leaf<P>) {
    return Leaf(f(tree.value))
  } else if (vars.isNotEmpty() && tree is Node<P>) {
    for (variable in vars) {
      if (tree.variable == variable) {
        return Node(variable, tree.partition.mapValues { apply1(vars, it.value, f) })
      }
    }
    throw IncorrectVariableReferences()
  }
  throw EmptyVariableReferences()
}

fun <P, Aux, Result> apply2(
    refs: MutableList<Ref<EntityType<*, *, *>>>,
    pdt1: Pdt<P>,
    pdt2: Pdt<Aux>,
    f: (P, Aux) -> Result
): Pdt<Result> {
  val vars = refs.toMutableList()
  return when {
    pdt1 is Leaf<P> && pdt2 is Leaf<Aux> -> Leaf(f(pdt1.value, pdt2.value))
    pdt1 is Leaf<P> && pdt2 is Node<Aux> ->
        Node(
            pdt2.variable,
            pdt2.partition.mapValues { (_, tree) ->
              apply1(vars, tree, partial2First(f, pdt1.value))
            })
    pdt1 is Node<P> && pdt2 is Leaf<Aux> ->
        Node(
            pdt1.variable,
            pdt1.partition.mapValues { (_, tree) ->
              apply1(vars, tree, partial2Second(f, pdt2.value))
            })
    vars.isNotEmpty() && pdt1 is Node<P> && pdt2 is Node<Aux> -> {
      compareVariables(vars, pdt1, pdt2, f)
    }
    else -> throw EmptyVariableReferences()
  }
}

fun <P, Aux, Result> compareVariables(
    vars: MutableList<Ref<EntityType<*, *, *>>>,
    pdt1: Node<P>,
    pdt2: Node<Aux>,
    f: (P, Aux) -> Result
): Pdt<Result> {
  val variable = vars.removeFirst()
  return if (pdt1.variable == variable) {
    if (pdt2.variable == variable) {
      Node(
          variable,
          merge2(pdt1.partition, pdt2.partition).mapValues { (_, value) ->
            apply2(vars, value.first, value.second, f)
          })
    } else {

      Node(variable, pdt1.partition.mapValues { (_, tree1) -> apply2(vars, tree1, pdt2, f) })
    }
  } else {
    if (pdt2.variable == variable) {
      Node(variable, pdt2.partition.mapValues { (_, tree2) -> apply2(vars, pdt1, tree2, f) })
    } else {
      apply2(vars, pdt1, pdt2, f)
    }
  }
}

fun <P1, P2> merge2(
    part1: Map<RefId, Pdt<P1>>,
    part2: Map<RefId, Pdt<P2>>
): Map<RefId, Pair<Pdt<P1>, Pdt<P2>>> {
  val result = mutableMapOf<RefId, Pair<Pdt<P1>, Pdt<P2>>>()
  part1.entries.forEach { (refId1, pdt1) ->
    part2[refId1]?.let { pdt2 -> result[refId1] = pdt1 to pdt2 }
  }
  return result
}

fun <P1, P3> merge3(
    part1: Map<RefId, Pdt<P1>>,
    part2: Map<RefId, Pdt<P1>>,
    part3: Map<RefId, Pdt<P3>>
): Map<RefId, Triple<Pdt<P1>, Pdt<P1>, Pdt<P3>>> {
  val result = mutableMapOf<RefId, Triple<Pdt<P1>, Pdt<P1>, Pdt<P3>>>()
  part1.mapNotNull { (refId1, pdt1) ->
    val found2 = part2[refId1]
    val found3 = part3[refId1]
    if (found2 != null && found3 != null) {
      result[refId1] = Triple(pdt1, found2, found3)
    } else null
  }
  return result
}

fun <P, Aux, Result> apply3(
    refs: MutableList<Ref<EntityType<*, *, *>>>,
    pdt1: Pdt<P>,
    pdt2: Pdt<P>,
    pdt3: Pdt<Aux>,
    f: (P, P, Aux) -> Result
): Pdt<Result> {
  val vars = refs.toMutableList()
  when {
    pdt1 is Leaf<P> && pdt2 is Leaf<P> && pdt3 is Leaf<Aux> ->
        return Leaf(f(pdt1.value, pdt2.value, pdt3.value))
    pdt1 is Node<P> && pdt2 is Leaf<P> && pdt3 is Leaf<Aux> ->
        return Node(
            pdt1.variable,
            pdt1.partition.mapValues { (_, tree) ->
              apply1(vars, tree, partial3BC(f, pdt2.value, pdt3.value))
            })
    pdt1 is Leaf<P> && pdt2 is Node<P> && pdt3 is Leaf<Aux> ->
        return Node(
            pdt2.variable,
            pdt2.partition.mapValues { (_, tree) ->
              apply1(vars, tree, partial3AC(f, pdt1.value, pdt3.value))
            })
    pdt1 is Leaf<P> && pdt2 is Leaf<P> && pdt3 is Node<Aux> ->
        return Node(
            pdt3.variable,
            pdt3.partition.mapValues {
              apply1(vars, it.value, partial3AB(f, pdt1.value, pdt2.value))
            })
    vars.isNotEmpty() && pdt1 is Node<P> && pdt2 is Node<P> && pdt3 is Leaf<Aux> ->
        return compareVariables(vars, pdt1, pdt2, partial3C(f, pdt3.value))
    vars.isNotEmpty() && pdt1 is Node<P> && pdt2 is Leaf<P> && pdt3 is Node<Aux> ->
        return compareVariables(vars, pdt1, pdt3, partial3B(f, pdt2.value))
    vars.isNotEmpty() && pdt1 is Leaf<P> && pdt2 is Node<P> && pdt3 is Node<Aux> ->
        return compareVariables(vars, pdt2, pdt3, partial3A(f, pdt1.value))
    vars.isNotEmpty() && pdt1 is Node<P> && pdt2 is Node<P> && pdt3 is Node<Aux> -> {
      val variable = vars.removeFirst()
      val equalTriple =
          (pdt1.variable == variable) to (pdt2.variable == variable) to (pdt3.variable == variable)
      return when (equalTriple) {
        (true to true) to true ->
            Node(
                variable,
                merge3(pdt1.partition, pdt2.partition, pdt3.partition).mapValues { (_, trees) ->
                  apply3(vars, trees.first, trees.second, trees.third, f)
                })
        (true to true) to false ->
            Node(
                variable,
                merge2(pdt1.partition, pdt2.partition).mapValues { (_, trees) ->
                  apply3(vars, trees.first, trees.second, pdt3, f)
                })
        (true to false) to true ->
            Node(
                variable,
                merge2(pdt1.partition, pdt3.partition).mapValues { (_, trees) ->
                  apply3(vars, trees.first, pdt2, trees.second, f)
                })
        (false to true) to true ->
            Node(
                variable,
                merge2(pdt2.partition, pdt3.partition).mapValues { (_, trees) ->
                  apply3(vars, pdt1, trees.first, trees.second, f)
                })
        (false to false) to true ->
            Node(
                variable,
                pdt3.partition.mapValues { (refId3, tree3) -> apply3(vars, pdt1, pdt2, tree3, f) })
        (false to true) to false ->
            Node(
                variable,
                pdt2.partition.mapValues { (refId2, tree2) -> apply3(vars, pdt1, tree2, pdt3, f) })
        (true to false) to false ->
            Node(
                variable,
                pdt1.partition.mapValues { (refId1, tree1) -> apply3(vars, tree1, pdt2, pdt3, f) })
        else -> apply3(vars, pdt1, pdt2, pdt3, f)
      }
    }
    else -> throw EmptyVariableReferences()
  }
}

fun hide(
    refs: MutableList<Ref<EntityType<*, *, *>>>,
    pdt: Pdt<Proof>,
    fleaf: (Ref<EntityType<*, *, *>>, Proof) -> Proof,
    fnode: (Ref<EntityType<*, *, *>>, List<Pair<RefId, Proof>>) -> Proof,
    newVar: Ref<EntityType<*, *, *>>
): Pdt<Proof> {
  return hideRec(refs.toMutableList(), pdt, fleaf, fnode, newVar)
}

private fun hideRec(
    vars: MutableList<Ref<EntityType<*, *, *>>>,
    pdt: Pdt<Proof>,
    fleaf: (Ref<EntityType<*, *, *>>, Proof) -> Proof,
    fnode: (Ref<EntityType<*, *, *>>, List<Pair<RefId, Proof>>) -> Proof,
    newVar: Ref<EntityType<*, *, *>>
): Pdt<Proof> {
  when {
    pdt is Leaf -> return Leaf(fleaf(newVar, pdt.value))
    vars.size == 1 && pdt is Node<Proof> -> {
      vars.removeFirst()
      return Leaf(
          fnode(
              newVar,
              pdt.partition.mapNotNull { (refId, leaf) ->
                if (leaf is Leaf<Proof>) {
                  refId to leaf.value
                } else null
              }))
    }
    vars.isNotEmpty() && pdt is Node<Proof> -> {
      val first = vars.removeFirst()
      return if (first == pdt.variable) {
        Node(
            first,
            pdt.partition.mapValues { (refId, tree) -> hide(vars, tree, fleaf, fnode, newVar) })
      } else {
        hide(vars, pdt, fleaf, fnode, newVar)
      }
    }
    else -> throw EmptyVariableReferences()
  }
}
