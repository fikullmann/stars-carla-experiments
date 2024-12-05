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

import tools.aqua.stars.carla.experiments.komfymc.holds
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.tsc.builder.*
import tools.aqua.stars.data.av.dataclasses.*


private const val FULL_TSC = "full TSC"
private const val LAYER_1_2 = "layer 1+2"
private const val LAYER_4 = "layer 4"
private const val LAYER_1_2_4 = "layer 1+2+4"
private const val LAYER_4_5 = "layer (4)+5"
private const val LAYER_PEDESTRIAN = "pedestrian"
private const val LAYER_MULTI_LANE_DYNAMIC_RELATIONS = "multi-lane-dynamic-relations"

fun dslTsc() =
    tsc<Actor, TickData, Segment, TickDataUnitSeconds, TickDataDifferenceSeconds> {
        all("TSCRoot") {
            projections {
                projectionRecursive(FULL_TSC) // all
                projection(LAYER_1_2) // static
                projection(LAYER_4) // dynamic
                projection(LAYER_1_2_4) // static + dynamic
                projection(LAYER_4_5) // environment
                projection(LAYER_PEDESTRIAN) // pedestrian
                projection(LAYER_MULTI_LANE_DYNAMIC_RELATIONS)
            }

            exclusive("Weather") {
                projections {
                    projectionRecursive(LAYER_4_5)
                    projectionRecursive(LAYER_PEDESTRIAN)
                }

                leaf("Clear") { condition { ctx -> weatherClearDSL.holds(ctx) } }
                leaf("Cloudy") { condition { ctx -> weatherCloudyDSL.holds(ctx) } }
                leaf("Wet") { condition { ctx -> weatherWetDSL.holds(ctx) } }
                leaf("Wet Cloudy") { condition { ctx -> weatherWetCloudyDSL.holds(ctx) } }
                leaf("Soft Rain") { condition { ctx -> weatherSoftRainDSL.holds(ctx) } }
                leaf("Mid Rain") { condition { ctx -> weatherMidRainDSL.holds(ctx) } }
                leaf("Hard Rain") { condition { ctx -> weatherHardRainDSL.holds(ctx) } }
            }

            exclusive("Road Type") {
                projections {
                    projection(LAYER_1_2)
                    projection(LAYER_4)
                    projection(LAYER_1_2_4)
                    projection(LAYER_PEDESTRIAN)
                    projection(LAYER_MULTI_LANE_DYNAMIC_RELATIONS)
                }

                all("Junction") {
                    condition { ctx -> isInJunctionDSL.holds(ctx) }

                    projections {
                        projection(LAYER_PEDESTRIAN)
                        projection(LAYER_1_2)
                        projection(LAYER_4)
                        projection(LAYER_1_2_4)
                    }

                    optional("Dynamic Relation") {
                        projections {
                            projection(LAYER_PEDESTRIAN)
                            projectionRecursive(LAYER_4)
                            projectionRecursive(LAYER_1_2_4)
                        }

                        leaf("Pedestrian Crossed") {
                            projections { projection(LAYER_PEDESTRIAN) }

                            condition { ctx -> pedestrianCrossedDSL.holds(ctx) }
                        }

                        leaf("Must Yield") {

                            condition { ctx -> mustYieldDSL.holds(ctx) }

                            monitors {
                                monitor("Did not yield") { ctx -> hasYieldedDSL.holds(ctx) }
                            }
                        }

                        leaf("Following Leading Vehicle") {
                            projections { projection(LAYER_4) }
                            condition { ctx -> followsDSL.holds(ctx) }
                        }
                    }

                    exclusive("Maneuver") {
                        projections {
                            projectionRecursive(LAYER_1_2)
                            projectionRecursive(LAYER_1_2_4)
                        }

                        leaf("Lane Follow") { condition { ctx -> makesNoTurnDSL.holds(ctx) } }
                        leaf("Right Turn") { condition { ctx -> makesRightTurnDSL.holds(ctx) } }
                        leaf("Left Turn") { condition { ctx -> makesLeftTurnDSL.holds(ctx) } }
                    }
                }
                all("Multi-Lane") {
                    projections {
                        projection(LAYER_PEDESTRIAN)
                        projection(LAYER_1_2)
                        projection(LAYER_4)
                        projection(LAYER_1_2_4)
                        projection(LAYER_MULTI_LANE_DYNAMIC_RELATIONS)
                    }

                    condition { ctx -> isOnMultiLaneDSL.holds(ctx) }

                    optional("Dynamic Relation") {
                        projections {
                            projection(LAYER_PEDESTRIAN)
                            projectionRecursive(LAYER_4)
                            projectionRecursive(LAYER_1_2_4)
                            projectionRecursive(LAYER_MULTI_LANE_DYNAMIC_RELATIONS)
                        }
                        leaf("Oncoming traffic") {
                            condition { ctx -> oncomingDSL.holds(ctx) }
                        }
                        leaf("Overtaking") {
                            condition { ctx -> hasOvertakenDSL.holds(ctx) }
                            monitors { monitor("Right Overtaking") { ctx -> noRightOvertakingDSL.holds(ctx) } }
                        }
                        leaf("Pedestrian Crossed") {
                            projections { projection(LAYER_PEDESTRIAN) }

                            condition { ctx -> pedestrianCrossedDSL.holds(ctx) }
                        }
                        leaf("Following Leading Vehicle") {
                            projections { projection(LAYER_4) }

                            condition { ctx -> followsDSL.holds(ctx) }
                        }
                    }

                    exclusive("Maneuver") {
                        projections {
                            projectionRecursive(LAYER_1_2)
                            projectionRecursive(LAYER_1_2_4)
                        }
                        leaf("Lane Change") { condition { ctx -> changedLaneDSL.holds(ctx) } }
                        leaf("Lane Follow") { condition { ctx -> !changedLaneDSL.holds(ctx) } }
                    }

                    bounded("Stop Type", Pair(0, 1)) {
                        projections {
                            projectionRecursive(LAYER_1_2)
                            projectionRecursive(LAYER_1_2_4)
                        }

                        leaf("Has Red Light") {
                            condition { ctx -> hasRelevantRedLightDSL.holds(ctx) }
                            monitors { monitor("Crossed red light") { ctx -> !didCrossRedLightDSL.holds(ctx) } }
                        }
                    }
                }
                all("Single-Lane") {
                    projections {
                        projection(LAYER_PEDESTRIAN)
                        projection(LAYER_1_2)
                        projection(LAYER_4)
                        projection(LAYER_1_2_4)
                    }

                    condition { ctx -> isOnSingleLaneDSL.holds(ctx) }

                    optional("Dynamic Relation") {
                        projections {
                            projection(LAYER_PEDESTRIAN)
                            projectionRecursive(LAYER_4)
                            projectionRecursive(LAYER_1_2_4)
                        }

                        leaf("Oncoming traffic") {
                            condition { ctx -> oncomingDSL.holds(ctx) }
                        }

                        leaf("Pedestrian Crossed") {
                            projections { projection(LAYER_PEDESTRIAN) }

                            condition { ctx -> pedestrianCrossedDSL.holds(ctx) }
                        }

                        leaf("Following Leading Vehicle") {
                            projections {
                                projection(LAYER_4)
                                projection(LAYER_1_2_4)
                            }

                            condition { ctx -> followsDSL.holds(ctx) }
                        }
                    }

                    bounded("Stop Type", Pair(0, 1)) {
                        projections {
                            projectionRecursive(LAYER_1_2)
                            projectionRecursive(LAYER_1_2_4)
                        }

                        leaf("Has Stop Sign") {
                            condition { ctx -> hasStopSignDSL.holds(ctx) }
                            monitors { monitor("Stopped at stop sign") { ctx -> stopAtEndDSL.holds(ctx) } }
                        }
                        leaf("Has Yield Sign") { condition { ctx -> hasYieldSignDSL.holds(ctx) } }
                        leaf("Has Red Light") {

                            condition { ctx -> hasRelevantRedLightDSL.holds(ctx) }
                            monitors { monitor("Crossed red light") { ctx -> !didCrossRedLightDSL.holds(ctx) } }
                        }
                    }
                }
            }

            exclusive("Traffic Density") {
                projections {
                    projectionRecursive(LAYER_4_5)
                    projectionRecursive(LAYER_4)
                    projectionRecursive(LAYER_1_2_4)
                }

                leaf("High Traffic") { condition { ctx -> hasHighTrafficDensityDSL.holds(ctx) } }
                leaf("Middle Traffic") { condition { ctx -> hasMidTrafficDensityDSL.holds(ctx) } }
                leaf("Low Traffic") { condition { ctx -> hasLowTrafficDensityDSL.holds(ctx) } }
            }

            exclusive("Time of Day") {
                projections {
                    projectionRecursive(LAYER_4_5)
                    projectionRecursive(LAYER_PEDESTRIAN)
                }

                leaf("Sunset") { condition { ctx -> sunsetDSL.holds(ctx) } }

                leaf("Noon") { condition { ctx -> noonDSL.holds(ctx) } }
            }
        }
    }
