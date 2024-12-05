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

import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.RefId
import tools.aqua.stars.core.types.EntityType

sealed class Proof : Comparable<Proof> {
  abstract fun size(): Int

  abstract fun at(): TP

  override fun compareTo(other: Proof): Int = this.at().i - other.at().i
}

sealed class SatProof : Proof()

data class SatTT(val tp: TP) : SatProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class SatPred(val tp: TP) : SatProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class SatVar(val tp: TP, val name: String) : SatProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class SatNeg(val inner: ViolationProof) : SatProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + inner.size())
        return sizeCache ?: (1 + inner.size())
    }
  override fun at(): TP = inner.at()
}

data class SatOrL(val lhs: Proof) : SatProof() {
  override fun size() = 1 + lhs.size()

  override fun at(): TP = lhs.at()
}

data class SatOrR(val rhs: Proof) : SatProof() {
  override fun size() = 1 + rhs.size()

  override fun at(): TP = rhs.at()
}

data class SatAnd(val lhs: Proof, val rhs: Proof) : SatProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + lhs.size() + rhs.size())
        return sizeCache ?: (1 + lhs.size() + rhs.size())
    }

  override fun at(): TP = lhs.at()
}

data class SatImplL(val lhs: ViolationProof) : SatProof() {
  override fun size() = 1 + lhs.size()

  override fun at(): TP = lhs.at()
}

data class SatImplR(val rhs: SatProof) : SatProof() {
  override fun size() = 1 + rhs.size()

  override fun at(): TP = rhs.at()
}

data class SatIffSS(val lhs: SatProof, val rhs: SatProof) : SatProof() {
  override fun size() = 1 + lhs.size() + rhs.size()

  override fun at(): TP = lhs.at()
}

data class SatIffVV(val lhs: ViolationProof, val rhs: ViolationProof) : SatProof() {
  override fun size() = 1 + lhs.size() + rhs.size()

  override fun at(): TP = lhs.at()
}

data class SatPrev(val alpha: SatProof) : SatProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = TP(alpha.at().i + 1)
}

data class SatNext(val alpha: SatProof) : SatProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = TP(alpha.at().i - 1)
}

data class SatOnce(val tp: TP, val alpha: SatProof) : SatProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = tp
}

data class SatHistorically(val tp1: TP, val tp2: TP, val alphas: MutableList<SatProof>) :
    SatProof() {
  override fun size(): Int = 1 + alphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class SatHistoricallyOutL(val tp: TP) : SatProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class SatEventually(val tp: TP, val alpha: SatProof) : SatProof() {
  var sizeCache: Int? = null

  override fun size(): Int {
    sizeCache = sizeCache ?: (1 + alpha.size())
    return sizeCache ?: (1 + alpha.size())
  }

  override fun at(): TP = tp
}

data class SatAlways(val tp1: TP, val tp2: TP, val alphas: MutableList<SatProof>) : SatProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + alphas.sumOf { it.size() })
        return sizeCache ?: (1 + alphas.sumOf { it.size() })
    }
  override fun at(): TP = tp1
}

data class SatSince(val beta: SatProof, val alphas: MutableList<SatProof> = mutableListOf()) :
    SatProof() {
  override fun size(): Int = 1 + beta.size() + alphas.sumOf { it.size() }

  override fun at(): TP = if (alphas.isEmpty()) beta.at() else alphas.last().at()
}

data class SatUntil(val beta: SatProof, val alphas: MutableList<SatProof>) : SatProof() {
  override fun size(): Int = 1 + beta.size() + alphas.sumOf { it.size() }

  override fun at(): TP = if (alphas.isEmpty()) beta.at() else alphas.first().at()
}

data class SatExists<T : EntityType<*, *, *, *, *>>(
    val ref: Ref<T>,
    val witness: RefId?,
    val alpha: SatProof
) : SatProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + alpha.size())
        return sizeCache ?: (1 + alpha.size())
    }

  override fun at(): TP = alpha.at()
}

data class SatForall<T : EntityType<*, *, *, *, *>>(
    val ref: Ref<T>,
    val part: List<Pair<RefId?, SatProof>>
) : SatProof() {
  override fun size(): Int = 1 + part.fold(0) { acc, (_, proof) -> acc + proof.size() }

  override fun at(): TP = part.first().second.at()
}

data class SatForallNone<T : EntityType<*, *, *, *, *>>(val ref: Ref<T>, val tp: TP) : SatProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class SatPastMinPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val sAlphas: MutableList<Proof>
) : SatProof() {
  override fun size(): Int = 1 + sAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class SatPastMinAllSat(val tp1: TP) : SatProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp1
}

data class SatMinPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val sAlphas: MutableList<Proof>
) : SatProof() {
  override fun size(): Int = 1 + sAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class SatMinAllSat(val tp1: TP) : SatProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp1
}

data class SatPastMaxPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val sAlphas: MutableList<Proof>
) : SatProof() {
  override fun size(): Int = 1 + sAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class SatMaxPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val sAlphas: MutableList<Proof>
) : SatProof() {
  override fun size(): Int = 1 + sAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

sealed class ViolationProof : Proof()

data class VFF(val tp: TP) : ViolationProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class VPred(val tp: TP) : ViolationProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class VVar(val tp: TP, val name: String) : ViolationProof() {
  override fun size() = 1

  override fun at(): TP = tp
}

data class VNeg(val inner: Proof) : ViolationProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + inner.size())
        return sizeCache ?: (1 + inner.size())
    }

  override fun at(): TP = inner.at()
}

data class VAndL(val inner: Proof) : ViolationProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + inner.size())
        return sizeCache ?: (1 + inner.size())
    }

  override fun at(): TP = inner.at()
}

data class VAndR(val inner: Proof) : ViolationProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + inner.size())
        return sizeCache ?: (1 + inner.size())
    }

  override fun at(): TP = inner.at()
}

data class VOr(val lhs: Proof, val rhs: Proof) : ViolationProof() {

    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + lhs.size() + rhs.size())
        return sizeCache ?: (1 + lhs.size() + rhs.size())
    }

  override fun at(): TP = lhs.at()
}

data class VImpl(val lhs: Proof, val rhs: Proof) : ViolationProof() {
  override fun size(): Int = 1 + lhs.size() + rhs.size()

  override fun at(): TP = lhs.at()
}

data class VIffSV(val lhs: Proof, val rhs: Proof) : ViolationProof() {
  override fun size(): Int = 1 + lhs.size() + rhs.size()

  override fun at(): TP = lhs.at()
}

data class VIffVS(val lhs: Proof, val rhs: Proof) : ViolationProof() {
  override fun size(): Int = 1 + lhs.size() + rhs.size()

  override fun at(): TP = lhs.at()
}

data object VPrev0 : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = TP(0)
}

data class VPrevOutL(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VPrevOutR(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VPrev(val alpha: Proof) : ViolationProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = TP(alpha.at().i + 1)
}

data class VNextOutL(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VNextOutR(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VNext(val alpha: Proof) : ViolationProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = TP(alpha.at().i - 1)
}

data class VNextInf(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VOnceOutL(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VOnce(val tp1: TP, val tp2: TP, val alpha: MutableList<Proof>) : ViolationProof() {
  override fun size(): Int = 1 + alpha.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VHistorically(val tp: TP, val alpha: Proof) : ViolationProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = tp
}

data class VEventually(val tp1: TP, val tp2: TP, val alpha: MutableList<Proof>) : ViolationProof() {

  var sizeCache: Int? = null

  override fun size(): Int {
    if (sizeCache == null) sizeCache = (1 + alpha.sumOf { it.size() })
    return sizeCache ?: (1 + alpha.sumOf { it.size() })
  }

  override fun at(): TP = tp1
}

data class VAlways(val tp: TP, val alpha: Proof) : ViolationProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + alpha.size())
        return sizeCache ?: (1 + alpha.size())
    }

  override fun at(): TP = tp
}

data class VSince(var tp: TP, val vAlpha: Proof, val vBetas: MutableList<Proof>) :
    ViolationProof() {
  override fun size(): Int = 1 + vAlpha.size() + vBetas.sumOf { it.size() }

  override fun at(): TP = tp
}

data class VSinceInf(val tp1: TP, val tp2: TP, val vBetas: MutableList<Proof>) : ViolationProof() {
  override fun size(): Int = 1 + vBetas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VSinceOutL(val tp: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VUntil(var tp: TP, val vAlpha: Proof, val vBetas: MutableList<Proof>) :
    ViolationProof() {
  override fun size(): Int = 1 + vAlpha.size() + vBetas.sumOf { it.size() }

  override fun at(): TP = tp
}

data class VUntilInf(val tp1: TP, val tp2: TP, val vBetas: MutableList<Proof>) : ViolationProof() {
  override fun size(): Int = 1 + vBetas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VExists<T : EntityType<*, *, *, *, *>>(
    val ref: Ref<T>,
    val part: List<Pair<RefId?, ViolationProof>>
) : ViolationProof() {
    var sizeCache: Int? = null

    override fun size(): Int {
        sizeCache = sizeCache ?: (1 + part.sumOf { (_, proof) -> proof.size() })
        return sizeCache ?: (1 + part.sumOf { (_, proof) -> proof.size()})
    }
  //override fun size(): Int = 1 + part.fold(0) { acc, (_, proof) -> acc + proof.size() }

  override fun at(): TP = part.first().second.at()
}

data class VExistsNone<T : EntityType<*, *, *, *, *>>(val ref: Ref<T>, val tp: TP) :
    ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp
}

data class VForall<T : EntityType<*, *, *, *, *>>(
    val ref: Ref<T>,
    val witness: RefId?,
    val alpha: ViolationProof
) : ViolationProof() {
  override fun size(): Int = 1 + alpha.size()

  override fun at(): TP = alpha.at()
}

data class VPastMinPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val vAlphas: MutableList<ViolationProof>
) : ViolationProof() {
  override fun size(): Int = 1 + vAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VMinPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val vAlphas: MutableList<ViolationProof>
) : ViolationProof() {
  override fun size(): Int = 1 + vAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VPastMaxPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val vAlphas: MutableList<ViolationProof>
) : ViolationProof() {
  override fun size(): Int = 1 + vAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VPastMaxAllSat(val tp1: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp1
}

data class VMaxPrev(
    val tp1: TP,
    val tp2: TP,
    val fraction: Double,
    val vAlphas: MutableList<ViolationProof>
) : ViolationProof() {
  override fun size(): Int = 1 + vAlphas.sumOf { it.size() }

  override fun at(): TP = tp1
}

data class VMaxAllSat(val tp1: TP) : ViolationProof() {
  override fun size(): Int = 1

  override fun at(): TP = tp1
}

data object ErrorProof : Proof() {
  override fun size(): Int = 0

  override fun at(): TP = TP(-1)
}

fun Proof.minp(other: Proof): Proof = if (this.size() < other.size()) this else other

fun minpList(list: List<Proof>): Proof = list.minBy { it.size() }
