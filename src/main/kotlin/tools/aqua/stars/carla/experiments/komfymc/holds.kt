package tools.aqua.stars.carla.experiments.komfymc

import tools.aqua.stars.carla.experiments.komfymc.dsl.FormulaBuilder
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.holds
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.types.*
import tools.aqua.stars.data.av.dataclasses.Vehicle

fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
((Ref<Vehicle>) -> FormulaBuilder).holds(
    ctx: (PredicateContext<E, T, S, U, D>)
): Boolean = ctx.holds(this)

fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
        ((Ref<Vehicle>) -> FormulaBuilder).holds(
    ctx: (PredicateContext<E, T, S, U, D>),
    entityId: Int
): Boolean = ctx.holds(this, Ref<Vehicle>(entityId))
fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
        ((Ref<Vehicle>) -> FormulaBuilder).holds(
    ctx: (PredicateContext<E, T, S, U, D>),
    entity: Vehicle
): Boolean = ctx.holds(this, Ref(entity.id))


fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
(PredicateContext<E, T, S, U, D>).holds(
    predicate: ((Ref<Vehicle>) -> FormulaBuilder),
    ref: Ref<Vehicle> = Ref(primaryEntityId),
    tick: U = segment.ticks.keys.first(),
): Boolean {
    val predicateHolds = predicate.holds(ref)
    val tp = segment.ticks.keys.indexOf(tick)
    val result = eval(segment, predicateHolds)[tp]
    return (result is SatProof)
}

fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
((Ref<Vehicle>, Ref<Vehicle>) -> FormulaBuilder).holds(
    ctx: (PredicateContext<E, T, S, U, D>),
    first: Vehicle,
    second: Vehicle
): Boolean = ctx.holds(this, ctx.segment.ticks.keys.first(), Ref(first.id), Ref(second.id))

fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
        ((Ref<Vehicle>, Ref<Vehicle>) -> FormulaBuilder).holds(
    ctx: (PredicateContext<E, T, S, U, D>),
    first: Int,
    second: Int
): Boolean = ctx.holds(this, ctx.segment.ticks.keys.first(), Ref(first), Ref(second))

fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U : TickUnit<U, D>,
        D : TickDifference<D>>
toCondTest(
    ctx: (PredicateContext<E, T, S, U, D>),
    predicate: (Ref<Vehicle>) -> FormulaBuilder
): Boolean {
    val formula = predicate.holds(Ref(ctx.primaryEntityId))
    return eval(ctx.segment, formula).firstOrNull()?.let { asdf ->
        if (asdf is SatProof) {
            true
        } else {
            false
        }
    }!!
}
fun <E : EntityType<E, T, S, U, D>,
        T : TickDataType<E, T, S, U, D>,
        S : SegmentType<E, T, S, U, D>,
        U: TickUnit<U, D>,
        D: TickDifference<D>>
        (PredicateContext<E, T, S, U, D>).holds(
    predicate: ((Ref<Vehicle>, Ref<Vehicle>) -> FormulaBuilder),
    tick: U = segment.ticks.keys.first(),
    entity1Id: Ref<Vehicle> = Ref(primaryEntityId),
    entity2Id: Ref<Vehicle>,
): Boolean {
    val predicateHolds = predicate.holds(entity1Id, entity2Id)
    val tp = segment.ticks.keys.indexOf(tick)
    val result = eval(segment, predicateHolds)[tp]
    return (result is SatProof)
}

fun Boolean.check(predicate: Boolean) {
    if (this != predicate) {
        println()
    }
}