package com.example.araspoint_map.backend.navigation

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import com.example.araspoint_map.backend.model.GeoNode
import java.io.InputStream
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class KmlPath(
    val name: String,
    val coordinates: List<GeoNode>
)

data class BuildingPolygon(
    val id: String,
    val name: String,
    val boundary: List<GeoNode>,
    val centerLat: Double,
    val centerLng: Double
)

data class ParsedCampusData(
    val paths: List<KmlPath>,
    val buildings: List<BuildingPolygon>,
    val nodes: List<GeoNode>,
    val adjacencyList: Map<String, List<GeoNode>>,
    val buildingToEntranceNode: Map<String, GeoNode>
)

object KmlParser {

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun parseKml(context: Context, fileName: String = "ArasPointUpdated.kml"): ParsedCampusData {
        val inputStream = context.assets.open(fileName)
        return parseKmlStream(inputStream)
    }

    fun parseKmlStream(inputStream: InputStream): ParsedCampusData {
        val paths = mutableListOf<KmlPath>()
        val buildings = mutableListOf<BuildingPolygon>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentTag = ""
        var currentName = ""
        var isPlacemark = false
        var isLineString = false
        var isPolygon = false
        var currentCoordinatesText = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("Placemark", ignoreCase = true)) {
                        isPlacemark = true
                        currentName = ""
                        isLineString = false
                        isPolygon = false
                        currentCoordinatesText = ""
                    } else if (isPlacemark && currentTag.equals("LineString", ignoreCase = true)) {
                        isLineString = true
                    } else if (isPlacemark && currentTag.equals("Polygon", ignoreCase = true)) {
                        isPolygon = true
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (isPlacemark && text.isNotEmpty()) {
                        if (currentTag.equals("name", ignoreCase = true) && currentName.isEmpty()) {
                            currentName = text
                        } else if (currentTag.equals("coordinates", ignoreCase = true)) {
                            currentCoordinatesText += " " + text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val endTag = parser.name
                    if (endTag.equals("Placemark", ignoreCase = true)) {
                        if (currentName.isNotEmpty() && currentCoordinatesText.isNotEmpty()) {
                            val geoNodes = parseCoordinates(currentCoordinatesText, currentName)
                            if (isLineString && geoNodes.isNotEmpty()) {
                                paths.add(KmlPath(currentName, geoNodes))
                            } else if (isPolygon && geoNodes.isNotEmpty()) {
                                val centerLat = geoNodes.map { it.lat }.average()
                                val centerLng = geoNodes.map { it.lng }.average()
                                val buildingId = mapNameToBuildingId(currentName)
                                buildings.add(BuildingPolygon(buildingId, currentName, geoNodes, centerLat, centerLng))
                            }
                        }
                        isPlacemark = false
                        isLineString = false
                        isPolygon = false
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream.close()

        return buildGraph(paths, buildings)
    }

    private fun parseCoordinates(rawCoords: String, pathName: String): List<GeoNode> {
        val result = mutableListOf<GeoNode>()
        val tokens = rawCoords.trim().split("\\s+".toRegex())
        var idx = 0
        for (token in tokens) {
            val parts = token.trim().split(",")
            if (parts.size >= 2) {
                try {
                    val lng = parts[0].toDouble()
                    val lat = parts[1].toDouble()
                    idx++
                    result.add(GeoNode("node_${pathName}_$idx", pathName, lat, lng))
                } catch (_: NumberFormatException) {}
            }
        }
        return result
    }

    private fun mapNameToBuildingId(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("HEB") -> "HEB"
            upper.contains("CTEB") -> "CTEB"
            upper.contains("GYMNASIUM") || upper.contains("JOSON") -> "GYM"
            upper.contains("TAAL") -> "TAAL"
            upper.contains("LEMERY") -> "LEMERY"
            upper.contains("IBAAN") -> "IBAAN"
            upper.contains("CAS") -> "CAS"
            upper.contains("HOSTEL") -> "HOSTEL"
            upper.contains("IMT") -> "IMT"
            upper.contains("LIAN") -> "LIAN"
            upper.contains("ROSARIO") -> "ROSARIO"
            upper.contains("INFIRMARY") -> "INFIRMARY"
            upper.contains("APACIBLE") -> "APACIBLE"
            upper.contains("ERMITA") -> "ERMITA"
            else -> name.replace(" ", "_").uppercase()
        }
    }

    private fun buildGraph(paths: List<KmlPath>, buildings: List<BuildingPolygon>): ParsedCampusData {
        val uniqueNodes = mutableListOf<GeoNode>()
        val adjacencyMap = mutableMapOf<String, MutableList<GeoNode>>()

        fun getOrCreateSnappedNode(node: GeoNode): GeoNode {
            val existing = uniqueNodes.find { existing ->
                haversineDistance(existing.lat, existing.lng, node.lat, node.lng) <= 2.0
            }
            if (existing != null) {
                return existing
            }
            val canonicalNode = GeoNode("gn_${uniqueNodes.size + 1}", node.name, node.lat, node.lng)
            uniqueNodes.add(canonicalNode)
            adjacencyMap[canonicalNode.id] = mutableListOf()
            return canonicalNode
        }

        for (path in paths) {
            val coords = path.coordinates
            for (i in 0 until coords.size - 1) {
                val u = getOrCreateSnappedNode(coords[i])
                val v = getOrCreateSnappedNode(coords[i + 1])
                if (u.id != v.id) {
                    val uList = adjacencyMap.getOrPut(u.id) { mutableListOf() }
                    val vList = adjacencyMap.getOrPut(v.id) { mutableListOf() }
                    if (uList.none { it.id == v.id }) uList.add(v)
                    if (vList.none { it.id == u.id }) vList.add(u)
                }
            }
        }

        ensureGraphConnected(uniqueNodes, adjacencyMap)

        val buildingToEntranceNode = mutableMapOf<String, GeoNode>()
        for (b in buildings) {
            val closestNode = uniqueNodes.minByOrNull { n ->
                haversineDistance(n.lat, n.lng, b.centerLat, b.centerLng)
            }
            if (closestNode != null) {
                buildingToEntranceNode[b.id] = closestNode
            }
        }

        return ParsedCampusData(
            paths = paths,
            buildings = buildings,
            nodes = uniqueNodes,
            adjacencyList = adjacencyMap,
            buildingToEntranceNode = buildingToEntranceNode
        )
    }

    private fun ensureGraphConnected(uniqueNodes: List<GeoNode>, adjacencyMap: MutableMap<String, MutableList<GeoNode>>) {
        if (uniqueNodes.isEmpty()) return

        fun getConnectedComponents(): MutableList<MutableSet<String>> {
            val visited = mutableSetOf<String>()
            val components = mutableListOf<MutableSet<String>>()

            for (node in uniqueNodes) {
                if (node.id !in visited) {
                    val component = mutableSetOf<String>()
                    val queue = ArrayDeque<String>()
                    queue.add(node.id)
                    visited.add(node.id)

                    while (queue.isNotEmpty()) {
                        val curr = queue.removeFirst()
                        component.add(curr)
                        for (neighbor in adjacencyMap[curr] ?: emptyList<GeoNode>()) {
                            if (neighbor.id !in visited) {
                                visited.add(neighbor.id)
                                queue.add(neighbor.id)
                            }
                        }
                    }
                    components.add(component)
                }
            }
            return components
        }

        var components = getConnectedComponents()
        while (components.size > 1) {
            val mainComponent = components[0]
            var bestPair: Triple<GeoNode, GeoNode, Double>? = null
            var bestOtherIndex = -1

            for (i in 1 until components.size) {
                val otherComponent = components[i]
                for (id1 in mainComponent) {
                    val n1 = uniqueNodes.find { it.id == id1 } ?: continue
                    for (id2 in otherComponent) {
                        val n2 = uniqueNodes.find { it.id == id2 } ?: continue
                        val dist = haversineDistance(n1.lat, n1.lng, n2.lat, n2.lng)
                        if (bestPair == null || dist < bestPair.third) {
                            bestPair = Triple(n1, n2, dist)
                            bestOtherIndex = i
                        }
                    }
                }
            }

            if (bestPair != null && bestOtherIndex != -1) {
                val (n1, n2, _) = bestPair
                val list1 = adjacencyMap.getOrPut(n1.id) { mutableListOf() }
                val list2 = adjacencyMap.getOrPut(n2.id) { mutableListOf() }
                if (list1.none { it.id == n2.id }) list1.add(n2)
                if (list2.none { it.id == n1.id }) list2.add(n1)

                mainComponent.addAll(components[bestOtherIndex])
                components.removeAt(bestOtherIndex)
            } else {
                break
            }
            components = getConnectedComponents()
        }
    }
}
