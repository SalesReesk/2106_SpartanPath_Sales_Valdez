package com.example.araspoint_map

import com.example.araspoint_map.backend.model.GeoNode
import com.example.araspoint_map.backend.model.enums.TurnType
import com.example.araspoint_map.backend.navigation.CampusNavigationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusNavigationEngineTest {

    private val nodes = listOf(
        GeoNode("A", "Main Walk Node A", 14.0665, 120.6260),
        GeoNode("B", "Main Walk Node B", 14.0670, 120.6265),
        GeoNode("C", "Building C Entrance", 14.0675, 120.6270)
    )

    private val adjacencyList = mapOf(
        "A" to listOf(nodes[1]),
        "B" to listOf(nodes[0], nodes[2]),
        "C" to listOf(nodes[1])
    )

    private val engine = CampusNavigationEngine(nodes, adjacencyList)

    @Test
    fun testHaversineDistance() {
        val dist = engine.distanceBetween(14.0665, 120.6260, 14.0670, 120.6265)
        assertTrue(dist > 0.0)
    }

    @Test
    fun testAStarPathfinding() {
        val path = engine.findPathAStar("A", "C")
        assertEquals(3, path.size)
        assertEquals("A", path[0].id)
        assertEquals("B", path[1].id)
        assertEquals("C", path[2].id)
    }

    @Test
    fun testTurnInstructions() {
        val path = engine.findPathAStar("A", "C")
        val steps = engine.generateTurnInstructions(path)
        assertEquals(2, steps.size)
        assertEquals(TurnType.HEAD_STRAIGHT, steps[0].turnType)
        assertEquals(TurnType.ARRIVED, steps[1].turnType)
    }

    @Test
    fun testFindShortestPathFromLocation() {
        val fullPath = engine.findShortestPathFromLocation(14.06651, 120.62601, "C")
        assertTrue(fullPath.size >= 3)
        assertEquals("user_pos", fullPath[0].id)
        assertEquals("C", fullPath.last().id)
    }

    @Test
    fun test2MeterArrivalDetection() {
        val buildingBoundary = listOf(
            GeoNode("p1", "Poly 1", 14.06750, 120.62700),
            GeoNode("p2", "Poly 2", 14.06755, 120.62700),
            GeoNode("p3", "Poly 3", 14.06755, 120.62705),
            GeoNode("p4", "Poly 4", 14.06750, 120.62705)
        )
        val centerLat = 14.067525
        val centerLng = 120.627025

        // User is right inside/at boundary (very close, < 2m)
        val arrived = engine.checkArrival(14.06750, 120.62700, buildingBoundary, centerLat, centerLng, 2.0)
        assertTrue(arrived)

        // User is 100 meters away
        val farAway = engine.checkArrival(14.06000, 120.62000, buildingBoundary, centerLat, centerLng, 2.0)
        assertFalse(farAway)
    }
}
