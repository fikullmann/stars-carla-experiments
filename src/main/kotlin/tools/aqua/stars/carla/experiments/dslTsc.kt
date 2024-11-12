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

package tools.aqua.stars.carla.experiments

import tools.aqua.dsl.FormulaBuilder
import tools.aqua.dsl.holds
import tools.aqua.stars.carla.experiments.komfymc.SatProof
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.eval
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.tsc.TSC
import tools.aqua.stars.core.tsc.builder.*
import tools.aqua.stars.core.tsc.projection.proj
import tools.aqua.stars.core.tsc.projection.projRec
import tools.aqua.stars.core.types.EntityType
import tools.aqua.stars.core.types.SegmentType
import tools.aqua.stars.core.types.TickDataType
import tools.aqua.stars.data.av.dataclasses.Actor
import tools.aqua.stars.data.av.dataclasses.Segment
import tools.aqua.stars.data.av.dataclasses.TickData
import tools.aqua.stars.data.av.dataclasses.Vehicle

fun <E : EntityType<E, T, S>, T : TickDataType<E, T, S>, S : SegmentType<E, T, S>> toCond(
    predicate: (Ref<Vehicle>) -> FormulaBuilder
): ((PredicateContext<E, T, S>) -> Boolean) {
  return { ctx: PredicateContext<E, T, S> ->
    val predicateHolds = predicate.holds(Ref(ctx.primaryEntityId))
    eval(ctx.segment, predicateHolds).firstOrNull()?.let { firstProof -> firstProof is SatProof }
        ?: predicateHolds.let { true }.apply { throw Exception() }
  }
}

fun <E : EntityType<E, T, S>, T : TickDataType<E, T, S>, S : SegmentType<E, T, S>> toCondTest(
    predicate: (Ref<Vehicle>) -> FormulaBuilder
): (PredicateContext<E, T, S>) -> Boolean {
  return { ctx: PredicateContext<E, T, S> ->
    val formula = predicate.holds(Ref(ctx.primaryEntityId))
    eval(ctx.segment, formula).firstOrNull()?.let { asdf ->
      if (asdf is SatProof) {
        true
      } else {
        false
      }
    }!!
  }
}

fun dslTest() =
    TSC(
        root<Actor, TickData, Segment> {
          all("TSCRoot") {
            valueFunction = { "TSCRoot" }
            projectionIDs =
                mapOf(
                    projRec("all"),
                    proj("static"),
                    proj("dynamic"),
                    proj("static+dynamic"),
                    proj("environment"),
                    proj("pedestrian"),
                    proj("multi-lane-dynamic-relations"))
            bounded("Stop Type", Pair(0, 1)) {
              projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
              leaf("Has Red Light") {
                condition = toCond(hasRelevantRedLightDSL)
                monitorFunction = toCond(didNotCrossRedLightDSL)
              }
            }
            leaf("Has Red Light") {
              condition = toCond(hasRelevantRedLightDSL)
              monitorFunction = toCond(didNotCrossRedLightDSL)
            }
          }
        })

fun dslTsc() =
    TSC(
        root<Actor, TickData, Segment> {
          all("TSCRoot") {
            valueFunction = { "TSCRoot" }
            projectionIDs =
                mapOf(
                    projRec("all"),
                    proj("static"),
                    proj("dynamic"),
                    proj("static+dynamic"),
                    proj("environment"),
                    proj("pedestrian"),
                    proj("multi-lane-dynamic-relations"))
            exclusive("Weather") {
              projectionIDs = mapOf(projRec("environment"), projRec("pedestrian"))
              leaf("Clear") { condition = toCond(weatherClearDSL) }
              leaf("Cloudy") { condition = toCond(weatherCloudyDSL) }
              leaf("Wet") { condition = toCond(weatherWetDSL) }
              leaf("Wet Cloudy") { condition = toCond(weatherWetCloudyDSL) }
              leaf("Soft Rain") { condition = toCond(weatherSoftRainDSL) }
              leaf("Mid Rain") { condition = toCond(weatherMidRainDSL) }
              leaf("Hard Rain") { condition = toCond(weatherHardRainDSL) }
            }
            exclusive("Road Type") {
              projectionIDs =
                  mapOf(
                      proj("static"),
                      proj("dynamic"),
                      proj("static+dynamic"),
                      proj("pedestrian"),
                      proj("multi-lane-dynamic-relations"))
              all("Junction") {
                condition = toCond(isInJunctionDSL)
                projectionIDs =
                    mapOf(
                        proj("pedestrian"), proj("static"), proj("dynamic"), proj("static+dynamic"))
                optional("Dynamic Relation") {
                  projectionIDs =
                      mapOf(proj("pedestrian"), projRec("dynamic"), projRec("static+dynamic"))
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = toCond(pedestrianCrossedDSL)
                  }
                  leaf("Must Yield") {
                    condition = toCond(mustYieldDSL)
                    monitorFunction = toCond(hasYieldedDSL)
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"))
                    condition = toCond(followsDSL)
                  }
                }
                exclusive("Maneuver") {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Lane Follow") { condition = toCond(makesNoTurnDSL) }
                  leaf("Right Turn") { condition = toCond(makesRightTurnDSL) }
                  leaf("Left Turn") { condition = toCond(makesLeftTurnDSL) }
                }
              }
              all("Multi-Lane") {
                condition = toCond(isInMultiLaneDSL)
                projectionIDs =
                    mapOf(
                        proj("pedestrian"),
                        proj("static"),
                        proj("dynamic"),
                        proj("static+dynamic"),
                        proj("multi-lane-dynamic-relations"))
                optional("Dynamic Relation") {
                  projectionIDs =
                      mapOf(
                          proj("pedestrian"),
                          projRec("dynamic"),
                          projRec("static+dynamic"),
                          projRec("multi-lane-dynamic-relations"))
                  leaf("Oncoming traffic") { condition = toCond(oncomingDSL) }
                  leaf("Overtaking") {
                    condition = toCond(hasOvertakenDSL)
                    monitorFunction = toCond(noRightOvertakingDSL)
                  }
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = toCond(pedestrianCrossedDSL)
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"))
                    condition = toCond(followsDSL)
                  }
                }
                exclusive("Maneuver") {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Lane Change") { condition = toCond(changedLaneDSL) }
                  leaf("Lane Follow") { condition = toCond(notChangedLaneDSL) }
                }
                bounded("Stop Type", Pair(0, 1)) {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Has Red Light") {
                    condition = toCond(hasRelevantRedLightDSL)
                    monitorFunction = { ctx -> !didCrossRedLight.holds(ctx) }
                  }
                }
              }
              all("Single-Lane") {
                condition = toCond(isInSingleLaneDSL)
                projectionIDs =
                    mapOf(
                        proj("pedestrian"), proj("static"), proj("dynamic"), proj("static+dynamic"))
                optional("Dynamic Relation") {
                  projectionIDs =
                      mapOf(proj("pedestrian"), projRec("dynamic"), projRec("static+dynamic"))
                  leaf("Oncoming traffic") { condition = toCond(oncomingDSL) }
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = toCond(pedestrianCrossedDSL)
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"), proj("static+dynamic"))
                    condition = toCond(followsDSL)
                  }
                }
                bounded("Stop Type", Pair(0, 1)) {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Has Stop Sign") {
                    condition = toCond(hasStopSignDSL)
                    monitorFunction = toCond(stopAtEndDSL)
                  }
                  leaf("Has Yield Sign") { condition = toCond(hasYieldSignDSL) }
                  leaf("Has Red Light") {
                    condition = toCond(hasRelevantRedLightDSL)
                    monitorFunction = { ctx -> !didCrossRedLight.holds(ctx) }
                  }
                }
              }
            }
            exclusive("Traffic Density") {
              projectionIDs =
                  mapOf(projRec("environment"), projRec("dynamic"), projRec("static+dynamic"))
              leaf("High Traffic") { condition = toCond(hasHighTrafficDensityDSL) }
              leaf("Middle Traffic") { condition = toCond(hasMidTrafficDensityDSL) }
              leaf("Low Traffic") { condition = toCond(hasLowTrafficDensityDSL) }
            }
            exclusive("Time of Day") {
              projectionIDs = mapOf(projRec("environment"), projRec("pedestrian"))
              leaf("Sunset") { condition = toCond(sunsetDSL) }
              leaf("Noon") { condition = toCond(noonDSL) }
            }
          }
        })
