package com.example.araspoint_map

import com.example.araspoint_map.backend.model.Asset
import com.example.araspoint_map.backend.model.Building
import com.example.araspoint_map.backend.model.MaintenanceRecord
import com.example.araspoint_map.backend.model.Mappable
import com.example.araspoint_map.backend.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class MappablePolymorphismTest {

    @Test
    fun getFormattedMapTag_returnsPolymorphicTag() {
        val asset: Mappable = Asset(assetId = "AAMS-0001", assetName = "Projector")
        val building: Mappable = Building(id = "BLDG-CEA", name = "CEA Building")
        val user: Mappable = User(userId = "USER-001", email = "test@example.com")
        val report: Mappable = MaintenanceRecord(reportId = "REP-001", assetId = "AAMS-0001")

        assertEquals("[Asset] AAMS-0001", asset.getFormattedMapTag())
        assertEquals("[Building] BLDG-CEA", building.getFormattedMapTag())
        assertEquals("[User] USER-001", user.getFormattedMapTag())
        assertEquals("[MaintenanceRecord] REP-001", report.getFormattedMapTag())
    }
}
