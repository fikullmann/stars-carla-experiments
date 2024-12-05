package tools.aqua.stars.carla.experiments.roadType

import org.junit.jupiter.api.Assertions.assertFalse
import tools.aqua.stars.carla.experiments.*
import tools.aqua.stars.carla.experiments.komfymc.holds
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.data.av.dataclasses.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class changedLaneTest {
    private val singeLaneRoad: Road = emptyRoad(id = 0, isJunction = false)
    private val singleLane11: Lane = emptyLane(laneId = 1, road = singeLaneRoad, laneLength = 50.0)
    private val singleLane12: Lane = emptyLane(laneId = 2, road = singeLaneRoad, laneLength = 50.0)

    private val singeLaneRoad2: Road = emptyRoad(id = 1, isJunction = false)
    private val singleLane21: Lane = emptyLane(laneId = 1, road = singeLaneRoad2, laneLength = 50.0)
    private val block: Block = emptyBlock()

    private val vehicleId: Int = 0

    @BeforeTest
    fun setup() {
        singeLaneRoad.lanes = listOf(singleLane11, singleLane12)
        block.roads = listOf(singeLaneRoad)
        singeLaneRoad2.lanes = listOf(singleLane21)
    }

    @Test
    fun testdoesnotchangeLane() {
        val tickData1 = emptyTickData(currentTick = TickDataUnitSeconds(0.0), blocks = listOf(block))
        val tickData2 = emptyTickData(currentTick = TickDataUnitSeconds(1.0), blocks = listOf(block))
        val ego =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane11,
                positionOnLane = 10.0,
                tickData = tickData1,
                effVelocityMPH = 11.0)
        tickData1.entities = listOf(ego)
        val ego2 =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane11,
                positionOnLane = 12.0,
                tickData = tickData2,
                effVelocityMPH = 11.0)
        tickData2.entities = listOf(ego2)

        val segment = Segment(listOf(tickData1, tickData2), segmentSource = "")
        val ctx = PredicateContext(segment)
        assertFalse(changedLaneDSL.holds(ctx, vehicleId))
    }
    @Test
    fun testchangesLane() {
        val tickData1 = emptyTickData(currentTick = TickDataUnitSeconds(0.0), blocks = listOf(block))
        val tickData2 = emptyTickData(currentTick = TickDataUnitSeconds(1.0), blocks = listOf(block))
        val ego =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane11,
                positionOnLane = 10.0,
                tickData = tickData1,
                effVelocityMPH = 11.0)
        tickData1.entities = listOf(ego)
        val ego2 =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane12,
                positionOnLane = 12.0,
                tickData = tickData2,
                effVelocityMPH = 11.0)
        tickData2.entities = listOf(ego2)

        val segment = Segment(listOf(tickData1, tickData2), segmentSource = "")
        val ctx = PredicateContext(segment)
        assertTrue(changedLaneDSL.holds(ctx, vehicleId))
    }

    @Test
    fun changesRoad() {

        val tickData1 = emptyTickData(currentTick = TickDataUnitSeconds(0.0), blocks = listOf(block))
        val tickData2 = emptyTickData(currentTick = TickDataUnitSeconds(1.0), blocks = listOf(block))
        val ego =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane11,
                positionOnLane = 10.0,
                tickData = tickData1,
                effVelocityMPH = 11.0)
        tickData1.entities = listOf(ego)
        val ego2 =
            emptyVehicle(
                id = vehicleId,
                egoVehicle = true,
                lane = singleLane21,
                positionOnLane = 12.0,
                tickData = tickData2,
                effVelocityMPH = 11.0)
        tickData2.entities = listOf(ego2)

        val segment = Segment(listOf(tickData1, tickData2), segmentSource = "")
        val ctx = PredicateContext(segment)
        assertFalse(changedLaneDSL.holds(ctx, vehicleId))
    }
}