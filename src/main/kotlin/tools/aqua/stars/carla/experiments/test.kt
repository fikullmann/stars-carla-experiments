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

package tools.aqua.stars.carla.experiments

import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.tsc.TSC
import tools.aqua.stars.core.tsc.builder.*
import tools.aqua.stars.core.tsc.projection.proj
import tools.aqua.stars.core.tsc.projection.projRec
import tools.aqua.stars.data.av.dataclasses.Actor
import tools.aqua.stars.data.av.dataclasses.Segment
import tools.aqua.stars.data.av.dataclasses.TickData

enum class Option {
  Full,
  Weather,
  RoadType,
  TrafficDensity,
  TimeDay
}

fun test(x: Option, single: Boolean) {
  if (x == Option.Full) {
    if (!single) tscEvaluation(tsc())
    tscEvaluation(dslTsc())
  }
  if (x == Option.Weather) {
      if (!single) tscEvaluation(weather())
    tscEvaluation(dslWeather())
  }
  if (x == Option.RoadType) {
      if (!single) tscEvaluation(roadType())
    tscEvaluation(dslRoadType())
  }
  if (x == Option.TrafficDensity) {
      if (!single) tscEvaluation(trafficDensity())
    tscEvaluation(dslTrafficDensity())
  }
  if (x == Option.TimeDay) {
      if (!single) tscEvaluation(timeDay())
    tscEvaluation(dslTimeDay())
  }
}

fun weather() =
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
              leaf("Clear") { condition = PredicateContext<Actor, TickData, Segment>::weatherClear }
              leaf("Cloudy") {
                condition = PredicateContext<Actor, TickData, Segment>::weatherCloudy
              }
              leaf("Wet") { condition = PredicateContext<Actor, TickData, Segment>::weatherWet }
              leaf("Wet Cloudy") {
                condition = PredicateContext<Actor, TickData, Segment>::weatherWetCloudy
              }
              leaf("Soft Rain") {
                condition = PredicateContext<Actor, TickData, Segment>::weatherSoftRain
              }
              leaf("Mid Rain") {
                condition = PredicateContext<Actor, TickData, Segment>::weatherMidRain
              }
              leaf("Hard Rain") {
                condition = PredicateContext<Actor, TickData, Segment>::weatherHardRain
              }
            }
          }
        })

fun dslWeather() =
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
          }
        })

fun roadType() =
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
            exclusive("Road Type") {
              projectionIDs =
                  mapOf(
                      proj("static"),
                      proj("dynamic"),
                      proj("static+dynamic"),
                      proj("pedestrian"),
                      proj("multi-lane-dynamic-relations"))
              all("Junction") {
                condition = { ctx -> isInJunction.holds(ctx) }
                projectionIDs =
                    mapOf(
                        proj("pedestrian"), proj("static"), proj("dynamic"), proj("static+dynamic"))
                optional("Dynamic Relation") {
                  projectionIDs =
                      mapOf(proj("pedestrian"), projRec("dynamic"), projRec("static+dynamic"))
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = { ctx -> pedestrianCrossed.holds(ctx) }
                  }
                  leaf("Must Yield") {
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        mustYield.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                    monitorFunction = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        hasYielded.holds(ctx, actor2 = otherVehicleId).let {
                          if (it) {
                            true
                          } else {
                            false
                          }
                        }
                      }
                    }
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"))
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        follows.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                  }
                }
                exclusive("Maneuver") {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Lane Follow") { condition = { ctx -> makesNoTurn.holds(ctx) } }
                  leaf("Right Turn") { condition = { ctx -> makesRightTurn.holds(ctx) } }
                  leaf("Left Turn") { condition = { ctx -> makesLeftTurn.holds(ctx) } }
                }
              }
              all("Multi-Lane") {
                condition = { ctx ->
                  isInMultiLane.holds(ctx, ctx.segment.firstTickId, ctx.segment.primaryEntityId)
                }
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
                  leaf("Oncoming traffic") {
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        oncoming.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                  }
                  leaf("Overtaking") {
                    condition = { ctx -> hasOvertaken.holds(ctx) }
                    monitorFunction = { ctx -> noRightOvertaking.holds(ctx) }
                  }
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = { ctx -> pedestrianCrossed.holds(ctx) }
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"))
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        follows.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                  }
                }
                exclusive("Maneuver") {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Lane Change") { condition = { ctx -> changedLane.holds(ctx) } }
                  leaf("Lane Follow") { condition = { ctx -> !changedLane.holds(ctx) } }
                }
                bounded("Stop Type", Pair(0, 1)) {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Has Red Light") {
                    condition = { ctx -> hasRelevantRedLight.holds(ctx) }
                    monitorFunction = { ctx -> !didCrossRedLight.holds(ctx) }
                  }
                }
              }
              all("Single-Lane") {
                condition = { ctx ->
                  isInSingleLane.holds(ctx, ctx.segment.firstTickId, ctx.segment.primaryEntityId)
                }
                projectionIDs =
                    mapOf(
                        proj("pedestrian"), proj("static"), proj("dynamic"), proj("static+dynamic"))
                optional("Dynamic Relation") {
                  projectionIDs =
                      mapOf(proj("pedestrian"), projRec("dynamic"), projRec("static+dynamic"))
                  leaf("Oncoming traffic") {
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        oncoming.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                  }
                  leaf("Pedestrian Crossed") {
                    projectionIDs = mapOf(proj("pedestrian"))
                    condition = { ctx -> pedestrianCrossed.holds(ctx) }
                  }
                  leaf("Following Leading Vehicle") {
                    projectionIDs = mapOf(proj("dynamic"), proj("static+dynamic"))
                    condition = { ctx ->
                      ctx.entityIds.any { otherVehicleId ->
                        follows.holds(ctx, actor2 = otherVehicleId)
                      }
                    }
                  }
                }
                bounded("Stop Type", Pair(0, 1)) {
                  projectionIDs = mapOf(projRec("static"), projRec("static+dynamic"))
                  leaf("Has Stop Sign") {
                    condition = { ctx -> hasStopSign.holds(ctx) }
                    monitorFunction = { ctx -> stopAtEnd.holds(ctx) }
                  }
                  leaf("Has Yield Sign") { condition = { ctx -> hasYieldSign.holds(ctx) } }
                  leaf("Has Red Light") {
                    condition = { ctx -> hasRelevantRedLight.holds(ctx) }
                    monitorFunction = { ctx -> !didCrossRedLight.holds(ctx) }
                  }
                }
              }
            }
          }
        })

fun dslRoadType() =
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
          }
        })

fun trafficDensity() =
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
            exclusive("Traffic Density") {
              projectionIDs =
                  mapOf(projRec("environment"), projRec("dynamic"), projRec("static+dynamic"))
              leaf("High Traffic") { condition = { ctx -> hasHighTrafficDensity.holds(ctx) } }
              leaf("Middle Traffic") { condition = { ctx -> hasMidTrafficDensity.holds(ctx) } }
              leaf("Low Traffic") { condition = { ctx -> hasLowTrafficDensity.holds(ctx) } }
            }
          }
        })

fun dslTrafficDensity() =
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

            exclusive("Traffic Density") {
              projectionIDs =
                  mapOf(projRec("environment"), projRec("dynamic"), projRec("static+dynamic"))
              leaf("High Traffic") { condition = toCond(hasHighTrafficDensityDSL) }
              leaf("Middle Traffic") { condition = toCond(hasMidTrafficDensityDSL) }
              leaf("Low Traffic") { condition = toCond(hasLowTrafficDensityDSL) }
            }
          }
        })

fun timeDay() =
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
            exclusive("Time of Day") {
              projectionIDs = mapOf(projRec("environment"), projRec("pedestrian"))
              leaf("Sunset") { condition = PredicateContext<Actor, TickData, Segment>::sunset }
              leaf("Noon") { condition = ExperimentPredicateContext::noon }
            }
          }
        })

fun dslTimeDay() =
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

            exclusive("Time of Day") {
              projectionIDs = mapOf(projRec("environment"), projRec("pedestrian"))
              leaf("Sunset") { condition = toCond(sunsetDSL) }
              leaf("Noon") { condition = toCond(noonDSL) }
            }
          }
        })
