import tools.aqua.stars.carla.experiments.*
import tools.aqua.stars.carla.experiments.komfymc.SatProof
import tools.aqua.stars.carla.experiments.komfymc.dsl.FormulaBuilder.Companion.formula
import tools.aqua.stars.carla.experiments.komfymc.dsl.Ref
import tools.aqua.stars.carla.experiments.komfymc.dsl.holds
import tools.aqua.stars.carla.experiments.komfymc.eval
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.evaluation.BinaryPredicate.Companion.predicate
import tools.aqua.stars.data.av.dataclasses.*
import tools.aqua.stars.logic.kcmftbl.eventually
import kotlin.math.sign
import kotlin.system.measureTimeMillis
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class ComparisonTest {
    private val road0 = emptyRoad(id = 0)
    private val road0lane1 = emptyLane(laneId = 1, road = road0, laneLength = 50.0)

    private val road1 = emptyRoad(id = 1)
    private val road1lane1 = emptyLane(laneId = 1, road = road1, laneLength = 50.0)

    private val block = emptyBlock()

    @BeforeTest
    fun setup() {
        road0.lanes = listOf(road0lane1)
        road1.lanes = listOf(road1lane1)

        block.roads = listOf(road0, road1)
    }

    @Test
    fun test() {
        val tickDataList = mutableListOf<TickData>()

        for (i in 0..30) {
            val tickData =
                emptyTickData(currentTick = TickDataUnitSeconds(i.toDouble()), blocks = listOf(block))
            val elements = mutableListOf<Vehicle>()
            val ego =
                emptyVehicle(
                    egoVehicle = true,
                    id = 0,
                    tickData = tickData,
                    positionOnLane = 0.toDouble(),
                    lane = road0lane1)
            elements.add(ego)
            for (j in 1..100) {
                val v1 =
                    emptyVehicle(
                        egoVehicle = false,
                        id = 2 * j,
                        tickData = tickData,
                        positionOnLane = j.toDouble(),
                        lane = road0lane1)
                elements.add(v1)
            }
            tickData.entities = elements
            tickDataList.add(tickData)
        }
        val segment = Segment(tickDataList, segmentSource = "")
        val ctx = PredicateContext(segment)

        val formula = formula {
            exists { v1: Ref<Vehicle> ->
                eventually {
                    exists{ v2: Ref<Vehicle> ->
                        pred(v1, v2) { v1, v2 -> v1.id + 1 == v2.id } or pred(v1, v2) { v1, v2 -> v1.id -1 == v2.id }
                    }
                }
            }
        }
        val formula2 = formula {
            exists { v1: Ref<Vehicle> ->
                eventually {
                    pred(v1) { v1 ->
                        v1.tickData.vehicles
                            .any { vx ->
                                v1.id + 1 == vx.id || v1.id - 1 == vx.id
                            }
                    }
                }
            }
        }

        val x = measureTimeMillis {
            assertFalse(eval(segment, formula)[0] is SatProof)
        }
        val x2 = measureTimeMillis {
            assertFalse(eval(segment, formula2)[0] is SatProof)
        }
        println(x)
        println(x2)

        val y = measureTimeMillis {
            assertFalse(ctx.entityIds.any { otherVehicleId ->
                predicate(Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
                    eventually(v1) { v1 ->
                        v1.tickData.vehicles
                            .any { vx ->
                                v1.id + 1 == vx.id || v1.id - 1 == vx.id
                            }
                    }
                }.holds(ctx, TickDataUnitSeconds(0.0), 0, otherVehicleId)
            })
        }
        println(y)

    }
}