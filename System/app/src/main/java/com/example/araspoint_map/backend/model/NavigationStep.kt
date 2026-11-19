package com.example.araspoint_map.backend.model

import com.example.araspoint_map.backend.model.enums.TurnType

data class NavigationStep(
    val instruction: String = "",
    val distanceMeters: Double = 0.0,
    val turnType: TurnType = TurnType.HEAD_STRAIGHT,
    val targetNode: GeoNode = GeoNode()
)
