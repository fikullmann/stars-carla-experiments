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

import kotlin.math.abs
import kotlin.math.sign
import tools.aqua.dsl.FormulaBuilder.Companion.formula
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.data.av.dataclasses.*

val hasMidTrafficDensityDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.vehiclesInBlock(it.lane.road.block).size in 6..15 } }
}

val hasHighTrafficDensityDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.vehiclesInBlock(it.lane.road.block).size > 15 } }
}

val hasLowTrafficDensityDSL = formula { v: Ref<Vehicle> ->
  neg { hasHighTrafficDensityDSL.holds(v) or hasMidTrafficDensityDSL.holds(v) }
}

val changedLaneDSL = formula { v: Ref<Vehicle> ->
  binding(term(v) { v -> v.lane }) { l -> eventually { pred(v) { v -> l.with(v) != v.lane } } }
}

val notChangedLaneDSL = formula { v: Ref<Vehicle> ->
  neg {
    binding(term(v) { v -> v.lane }) { l -> eventually { pred(v) { v -> l.with(v) != v.lane } } }
  }
}

val onSameRoadDSL = formula { v1: Ref<Vehicle>, v2: Ref<Vehicle> ->
  pred(v1, v2) { fst, snd -> fst.lane.road == snd.lane.road }
}

val oncomingDSL = formula { v1: Ref<Vehicle> ->
  exists { v2: Ref<Vehicle> ->
    eventually {
      onSameRoadDSL.holds(v1, v2) and
          pred(v1, v2) { fst, snd -> fst.lane.laneId.sign == snd.lane.laneId.sign }
    }
  }
}

val isInJunctionDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.8) { pred(v) { it.lane.road.isJunction } }
}

val isInSingleLaneDSL = formula { v: Ref<Vehicle> ->
  neg { isInJunctionDSL.holds(v) } and
      minPrevalence(0.8) {
        pred(v) { predV ->
          predV.lane.road.lanes.filter { predV.lane.laneId.sign == it.laneId.sign }.size == 1
        }
      }
}
val isInMultiLaneDSL = formula { v: Ref<Vehicle> ->
  neg { isInJunctionDSL.holds(v) or isInSingleLaneDSL.holds(v) }
}

val sunsetDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.daytime == Daytime.Sunset } }
}
val noonDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.daytime == Daytime.Noon } }
}
val weatherClearDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.Clear } }
}
val weatherCloudyDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.Cloudy } }
}
val weatherWetDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.Wet } }
}
val weatherWetCloudyDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) {
    pred(v) { it.tickData.segment.tickData.first().weather.type == WeatherType.WetCloudy }
  }
}
val weatherSoftRainDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.SoftRainy } }
}
val weatherMidRainDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.MidRainy } }
}

val weatherHardRainDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.6) { pred(v) { it.tickData.weather.type == WeatherType.HardRainy } }
}

// used in behind
val soBetweenDSL = formula { ego: Ref<Vehicle>, v1: Ref<Vehicle> ->
  exists { v2: Ref<Vehicle> ->
    (pred(ego, v2) { ego, v2 -> ego.id != v2.id } or pred(v1, v2) { v1, v2 -> v1.id != v2.id }) and
        (pred(ego, v2) { ego, v2 -> ego.lane.uid == v2.lane.uid } or
            pred(v1, v2) { v1, v2 -> v1.lane.uid == v2.lane.uid }) and
        (pred(ego, v2) { ego, v2 -> ego.lane.uid != v2.lane.uid } or
            pred(ego, v2) { ego, v2 -> ego.positionOnLane < v2.positionOnLane }) and
        (pred(v1, v2) { v1, v2 -> v1.lane.uid != v2.lane.uid } or
            pred(v1, v2) { v1, v2 -> v1.positionOnLane > v2.positionOnLane })
  }
}

val behindDSL = formula { v1: Ref<Vehicle>, v2: Ref<Vehicle> ->
  (pred(v1, v2) { v1, v2 -> v1.lane.uid == v2.lane.uid } and
      pred(v1, v2) { v1, v2 -> v1.positionOnLane < v2.positionOnLane }) or
      (pred(v1, v2) { v1, v2 -> v1.lane.successorLanes.any { it.lane.uid == v2.lane.uid } } and
          (neg { soBetweenDSL.holds(v1, v2) }))
}

val followsDSL = formula { ego: Ref<Vehicle> ->
  exists { v1: Ref<Vehicle> ->
    eventually {
      globally(0.0 to 30.0) { behindDSL.holds(ego, v1) } and eventually(30.0 to 31.0) { tt() }
    }
  }
}

// used in inReach
val onSameLaneDSL = formula { v1: Ref<Actor>, v2: Ref<Actor> ->
  pred(v1, v2) { v1, v2 -> v1.lane.uid == v2.lane.uid }
}
val inReachDSL = formula { p: Ref<Pedestrian>, v: Ref<Vehicle> ->
  pred(p, v) { p, v -> p.lane.uid == v.lane.uid } and
      pred(p, v) { p, v -> (p.positionOnLane - v.positionOnLane) in 0.0..10.0 }
}
val pedestrianCrossedDSL = formula { v: Ref<Vehicle> ->
  eventually { exists { p: Ref<Pedestrian> -> inReachDSL.holds(p, v) } }
}
val sameDirectionDSL = formula { v1: Ref<Vehicle>, v2: Ref<Vehicle> ->
  onSameRoadDSL.holds(v1, v2) and
      pred(v1, v2) { v1, v2 -> v1.lane.laneId.sign == v2.lane.laneId.sign }
}

val isBehindDSL = formula { r1: Ref<Vehicle>, r2: Ref<Vehicle> ->
  sameDirectionDSL.holds(r1, r2) and
      pred(r1, r2) { r1, r2 -> (r1.positionOnLane + 2.0) < r2.positionOnLane }
}

val bothOver10MphDSL = formula { r1: Ref<Vehicle>, r2: Ref<Vehicle> ->
  pred(r1, r2) { r1, r2 -> r1.effVelocityInMPH > 10 } and
      pred(r1, r2) { r1, r2 -> r2.effVelocityInMPH > 10 }
}
val besidesDSL = formula { r1: Ref<Vehicle>, r2: Ref<Vehicle> ->
  sameDirectionDSL.holds(r1, r2) and
      pred(r1, r2) { r1, r2 -> abs(r1.positionOnLane - r2.positionOnLane) <= 2.0 }
}
val overtakingDSL = formula { r1: Ref<Vehicle>, r2: Ref<Vehicle> ->
  eventually {
    isBehindDSL.holds(r1, r2) and
        bothOver10MphDSL.holds(r1, r2) and
        next {
          until {
            isBehindDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
            besidesDSL.holds(r1, r2) and
                bothOver10MphDSL.holds(r1, r2) and
                next {
                  until {
                    besidesDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
                    isBehindDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
                  }
                }
          }
        }
  }
}
val hasOvertakenDSL = formula { v: Ref<Vehicle> ->
  exists { v2: Ref<Vehicle> -> overtakingDSL.holds(v, v2) }
}

val rightOfDSL = formula { v1: Ref<Vehicle>, v2: Ref<Vehicle> ->
  besidesDSL.holds(v1, v2) and pred(v1, v2) { v1, v2 -> abs(v1.lane.laneId) > abs(v2.lane.laneId) }
}

val rightOvertakingDSL = formula { r1: Ref<Vehicle>, r2: Ref<Vehicle> ->
  eventually {
    isBehindDSL.holds(r1, r2) and
        bothOver10MphDSL.holds(r1, r2) and
        next {
          until {
            isBehindDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
            rightOfDSL.holds(r1, r2) and
                bothOver10MphDSL.holds(r1, r2) and
                next {
                  until {
                    rightOfDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
                    isBehindDSL.holds(r1, r2) and bothOver10MphDSL.holds(r1, r2)
                  }
                }
          }
        }
  }
}

val noRightOvertakingDSL = formula { v1: Ref<Vehicle> ->
  forall { v2: Ref<Vehicle> -> neg { rightOvertakingDSL.holds(v1, v2) } }
}

val stoppedDSL = formula { v1: Ref<Vehicle> -> pred(v1) { it.effVelocityInMPH < 1.0 } }

val stopAtEndDSL = formula { v1: Ref<Vehicle> ->
  eventually { isAtEndOfRoadDSL.holds(v1) and stoppedDSL.holds(v1) }
}

val passedContactPointDSL = formula { v0: Ref<Vehicle>, v1: Ref<Vehicle> ->
  pred(v0, v1) { v0, v1 ->
    v0.lane.contactPointPos(v1.lane)?.let { it < v0.positionOnLane } ?: false
  }
}

val hasYieldedDSL = formula { v1: Ref<Vehicle> ->
  exists { v2: Ref<Vehicle> ->
    until {
      neg { passedContactPointDSL.holds(v1, v2) }
      passedContactPointDSL.holds(v2, v1)
    }
  }
}

val obeyedSpeedLimitDSL = formula { t1: Ref<Vehicle> ->
  globally { pred(t1) { it.effVelocityInMPH <= it.lane.speedAt(it.positionOnLane) } }
}

val hasRedLightDSL = formula { v: Ref<Vehicle> ->
  pred(v) { v ->
    v.lane.successorLanes.any { contactLaneInfo ->
      contactLaneInfo.lane.trafficLights.any { staticTrafficLight ->
        staticTrafficLight.getStateInTick(v.tickData) == TrafficLightState.Red
      }
    }
  }
}

val hasRelevantRedLightDSL = formula { v: Ref<Vehicle> ->
  eventually { hasRedLightDSL.holds(v) and isAtEndOfRoadDSL.holds(v) }
}

val didNotCrossRedLightDSL = formula { v: Ref<Vehicle> ->
  globally {
    hasRelevantRedLightDSL.holds(v) impl
        binding(term(v) { v -> v.lane.road }) { r ->
          next { pred(v) { v -> r.with(v) == v.lane.road } }
        }
  }
}
val isAtEndOfRoadDSL = formula { v: Ref<Vehicle> ->
  pred(v) { it.positionOnLane >= it.lane.laneLength - 3.0 }
}

val hasStopSignDSL = formula { v: Ref<Vehicle> -> eventually { pred(v) { it.lane.hasStopSign } } }

val hasYieldSignDSL = formula { v: Ref<Vehicle> -> eventually { pred(v) { it.lane.hasYieldSign } } }

val mustYieldDSL = formula { v1: Ref<Vehicle> ->
  exists { v2: Ref<Vehicle> ->
    eventually { pred(v1, v2) { v1, v2 -> v1.lane.yieldLanes.any { it.lane == v2.lane } } }
  }
}

val makesRightTurnDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.8) { pred(v) { it.lane.isTurningRight } }
}

val makesLeftTurnDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.8) { pred(v) { it.lane.isTurningLeft } }
}

val makesNoTurnDSL = formula { v: Ref<Vehicle> ->
  minPrevalence(0.8) { pred(v) { it.lane.isStraight } }
}
