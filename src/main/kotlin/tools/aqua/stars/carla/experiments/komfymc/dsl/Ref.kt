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

import kotlin.reflect.KClass
import tools.aqua.stars.carla.experiments.komfymc.*
import tools.aqua.stars.core.types.*

/**
 * Ref is used in the DSL to symbolize entities of a specific type. Function now() is used inside
 * predicates and terms of the DSL. nextTick() and allAtTick() are helper functions for the model
 * checker.
 *
 * @param id denotes the id of the entity this Ref will access with now()
 * @param fixed denotes whether this Ref is for a primary entity - thus cannot be changed - or not
 */
open class Ref<E1 : EntityType<*, *, *>>(
    private val kClass: KClass<E1>,
    id: Int? = null,
    val fixed: Boolean = false
) {
  /** id of a fixed object (primary Entity) */
  var id: Int? = id
    set(value) {
      if (!fixed) field = value else throw Exception("The Id of a fixed Ref can not be changed.")
    }

  private var entity: E1? = null
  private var tickIdx: Int = -1
    set(value) {
      field = value
      entities = tickDataType[value].entities.filterIsInstance(kClass.java)
    }

  private var entities: List<E1>? = listOf()

  /**
   * returns the entity with the given id at the given tickData. Before now() is called, the id must
   * be set and the correct tickdatatype must be specified for Ref
   */
  fun now(): E1 {
    setToGlobalTick()
    if (fixed) {
      require(id != null)
      return entities?.firstOrNull { it.id == id }
          ?: throw Exception(
              "ID: $id, tickIdx: $tickIdx, globalTick: $globalTickIdx - There are no entities of this id at the current tick. Make sure the correct id and globalTick is given.")
    } else {
      require(entity != null)
      return (entity as E1)
          ?: throw Exception(
              "$id There are no entities of this id at the current tick. Make sure the correct id and globalTick is given.")
    }
  }

  /**
   * returns all entities at the given tick: correct global tick must be specified for Ref before
   * calling
   */
  fun allAtTick(): List<E1> {
    setToGlobalTick()
    return entities
        ?: throw Exception(
            "" +
                "There are no entities at all. Give Ref entities through Ref.setSegment(segment) before calling this method.")
  }
  /** cycles through all the entities of a tick and calls phi after correctly assigning Ref. */
  inline fun cycleEntitiesAtTick(tp: TP, phi: (EntityType<*, *, *>) -> Boolean) =
      allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc, e ->
        acc[RefId(e.id)] = Leaf(if (phi.invoke(e)) SatPred(tp) else VPred(tp))
        acc
      }

  inline fun cycleBinaryEntitiesAtTick(
      tp: TP,
      phi: (EntityType<*, *, *>, EntityType<*, *, *>) -> Boolean,
      ref2: Ref<out EntityType<*, *, *>>
  ) =
      allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc1, e1 ->
        val part2 =
            ref2.allAtTick().fold(mutableMapOf<RefId, Pdt<Proof>>()) { acc2, e2 ->
              acc2[RefId(e2.id)] = Leaf(if (phi.invoke(e1, e2)) SatPred(tp) else VPred(tp))
              acc2
            }
        acc1[RefId(e1.id)] = Node(ref2, part2)
        acc1
      }

  /** sets tick to the next tick, updates entities accordingly */
  private fun setToGlobalTick() {
    if (tickIdx != globalTickIdx) {
      tickIdx = globalTickIdx
      entities = tickDataType[tickIdx].entities.filterIsInstance(kClass.java)
    }
  }

  companion object {
    var globalTickIdx: Int = -1
    lateinit var tickDataType: List<TickDataType<*, *, *>>

    fun <E : EntityType<E, T, S>, T : TickDataType<E, T, S>, S : SegmentType<E, T, S>> setSegment(
        segment: S
    ) {
      tickDataType = segment.tickData
      globalTickIdx = 0
    }

    inline fun <E : EntityType<E, T, S>, T : TickDataType<E, T, S>, S : SegmentType<E, T, S>> cycle(
        segment: S,
        action: (index: Int, tick: Double) -> Unit
    ) {
      setSegment(segment)
      tickDataType.forEachIndexed { idx, tickDataType ->
        globalTickIdx = idx
        action(idx, tickDataType.currentTick)
      }
    }

    /** helper function to create instances of Ref without needing an explicit class parameter */
    inline operator fun <
        reified E1 : E,
        E : EntityType<E, T, S>,
        T : TickDataType<E, T, S>,
        S : SegmentType<E, T, S>> invoke(): Ref<E1> = Ref(E1::class)

    inline operator fun <
        reified E1 : EntityType<*, *, *>,
    > invoke(id: Int): Ref<E1> = Ref(E1::class, id, true)

    inline operator fun <
        reified E1 : E,
        E : EntityType<E, T, S>,
        T : TickDataType<E, T, S>,
        S : SegmentType<E, T, S>> invoke(entity: E1): Ref<E1> = Ref(E1::class, entity.id, true)
  }
}

data class RefId(val i: Int)
