package com.example.araspoint_map.backend.model

interface Mappable {
    val identifier: String

    fun getFormattedMapTag(): String {
        return "[${javaClass.simpleName}] $identifier"
    }
}
