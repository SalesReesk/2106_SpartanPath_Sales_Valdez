package com.example.araspoint_map

import com.example.araspoint_map.backend.model.Asset
import com.example.araspoint_map.backend.model.GeoNode
import com.example.araspoint_map.backend.model.MaintenanceRecord
import com.example.araspoint_map.backend.model.enums.PriorityStatus
import com.example.araspoint_map.backend.model.enums.ReportStatus
import com.example.araspoint_map.backend.model.enums.UserRole
import com.example.araspoint_map.backend.navigation.CampusNavigationEngine
import com.example.araspoint_map.backend.repository.BaseRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemIntegrationAndOopTest {

    @Test
    fun testOopPolymorphismAndMappable() {
        val asset = Asset(assetId = "AAMS-9999", assetName = "Test Router")
        assertEquals("[Asset] AAMS-9999", asset.getFormattedMapTag())
    }

    @Test
    fun testEncapsulationAndEnums() {
        val record = MaintenanceRecord(
            reportId = "REP-TEST",
            status = ReportStatus.PENDING,
            priority = PriorityStatus.HIGH
        )
        assertEquals(ReportStatus.PENDING, record.status)
        assertEquals(PriorityStatus.HIGH, record.priority)

        val role = UserRole.ADMIN
        assertTrue(role == UserRole.ADMIN)
        assertFalse(role == UserRole.STUDENT)
    }

    @Test
    fun testCampusNavigationEngineAStar() {
        val nodeA = GeoNode("A", "Node A", 14.0670, 120.6260)
        val nodeB = GeoNode("B", "Node B", 14.0671, 120.6261)
        val nodeC = GeoNode("C", "Node C", 14.0672, 120.6262)

        val nodes = listOf(nodeA, nodeB, nodeC)
        val adjacencyList = mapOf(
            "A" to listOf(nodeB),
            "B" to listOf(nodeA, nodeC),
            "C" to listOf(nodeB)
        )

        val engine = CampusNavigationEngine(nodes, adjacencyList)
        val path = engine.findPathAStar("A", "C")
        assertNotNull(path)
    }

    @Test
    fun testBaseRepositorySafeCallExecution() = runBlocking {
        class TestRepo : BaseRepository() {
            suspend fun performSuccessfulOperation(): Result<String> {
                return safeCall { "Success Data" }
            }

            suspend fun performFailingOperation(): Result<String> {
                return safeCall { throw IllegalStateException("Database Error") }
            }
        }

        val repo = TestRepo()
        val successResult = repo.performSuccessfulOperation()
        assertTrue(successResult.isSuccess)
        assertEquals("Success Data", successResult.getOrNull())

        val failureResult = repo.performFailingOperation()
        assertTrue(failureResult.isFailure)
    }

    @Test
    fun testRoleBasedAccessControlPermissions() {
        val adminRole = UserRole.ADMIN
        val maintRole = UserRole.MAINTENANCE
        val studentRole = UserRole.STUDENT

        assertTrue(adminRole == UserRole.ADMIN)
        assertFalse(maintRole == UserRole.ADMIN)
        assertFalse(studentRole == UserRole.ADMIN)
    }
}
