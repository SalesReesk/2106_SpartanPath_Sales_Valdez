package com.example.araspoint_map.backend.navigation

import com.example.araspoint_map.backend.model.GeoNode
import com.example.araspoint_map.backend.model.NavigationStep
import com.example.araspoint_map.backend.model.enums.TurnType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CampusNavigationEngine(
    val nodes: List<GeoNode>,
    val adjacencyList: Map<String, List<GeoNode>>
) {

    private var activePath: MutableList<GeoNode> = mutableListOf()
    private var destinationNode: GeoNode? = null
    val offRouteThresholdMeters = 12.0

    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)

        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun findNearestNode(lat: Double, lng: Double): GeoNode? {
        if (nodes.isEmpty()) return null
        return nodes.minByOrNull { distanceBetween(lat, lng, it.lat, it.lng) }
    }

    fun findShortestPathFromLocation(
        userLat: Double,
        userLng: Double,
        targetNodeId: String
    ): List<GeoNode> {
        val userGeoNode = GeoNode("user_pos", "Current Location", userLat, userLng)
        val targetNode = nodes.find { it.id == targetNodeId } ?: GeoNode(targetNodeId, "Destination", userLat, userLng)

        val nearestStart = findNearestNode(userLat, userLng)
        if (nearestStart == null) {
            return listOf(userGeoNode, targetNode)
        }

        val pathOnGraph = findPathAStar(nearestStart.id, targetNodeId)
        if (pathOnGraph.isEmpty()) {
            return listOf(userGeoNode, targetNode)
        }

        val fullPath = mutableListOf(userGeoNode)
        fullPath.addAll(pathOnGraph)
        activePath = fullPath
        return fullPath
    }

    fun findPathAStar(startNodeId: String, targetNodeId: String): List<GeoNode> {
        val start = nodes.find { it.id == startNodeId } ?: return emptyList()
        val target = nodes.find { it.id == targetNodeId } ?: return emptyList()
        destinationNode = target

        val gScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val fScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val cameFrom = mutableMapOf<String, String>()

        gScore[start.id] = 0.0
        fScore[start.id] = distanceBetween(start.lat, start.lng, target.lat, target.lng)

        val openSet = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        openSet.add(Pair(start.id, fScore[start.id]!!))

        while (openSet.isNotEmpty()) {
            val (currentId, _) = openSet.poll()!!

            if (currentId == target.id) {
                val path = mutableListOf<GeoNode>()
                var curr: String? = currentId
                while (curr != null) {
                    nodes.find { it.id == curr }?.let { path.add(0, it) }
                    curr = cameFrom[curr]
                }
                activePath = path
                return path
            }

            val current = nodes.find { it.id == currentId } ?: continue
            val neighbors = adjacencyList[currentId] ?: emptyList()

            for (neighbor in neighbors) {
                val tentativeGScore = gScore.getValue(currentId) + distanceBetween(current.lat, current.lng, neighbor.lat, neighbor.lng)
                if (tentativeGScore < gScore.getValue(neighbor.id)) {
                    cameFrom[neighbor.id] = currentId
                    gScore[neighbor.id] = tentativeGScore
                    val f = tentativeGScore + distanceBetween(neighbor.lat, neighbor.lng, target.lat, target.lng)
                    fScore[neighbor.id] = f
                    openSet.add(Pair(neighbor.id, f))
                }
            }
        }
        return emptyList()
    }

    fun generateTurnInstructions(path: List<GeoNode>): List<NavigationStep> {
        if (path.size < 2) return emptyList()
        val steps = mutableListOf<NavigationStep>()

        var previousBearing = calculateBearing(path[0].lat, path[0].lng, path[1].lat, path[1].lng)

        for (i in 0 until path.size - 1) {
            val curr = path[i]
            val next = path[i + 1]
            val stepDist = distanceBetween(curr.lat, curr.lng, next.lat, next.lng)
            val currentBearing = calculateBearing(curr.lat, curr.lng, next.lat, next.lng)

            val turnAngle = (currentBearing - previousBearing + 540) % 360 - 180
            val turnType = when {
                i == path.size - 2 -> TurnType.ARRIVED
                abs(turnAngle) < 20.0 -> TurnType.HEAD_STRAIGHT
                turnAngle in 20.0..65.0 -> TurnType.TURN_SLIGHT_RIGHT
                turnAngle in 65.0..115.0 -> TurnType.TURN_RIGHT
                turnAngle > 115.0 -> TurnType.TURN_SHARP_RIGHT
                turnAngle in -65.0..-20.0 -> TurnType.TURN_SLIGHT_LEFT
                turnAngle in -115.0..-65.0 -> TurnType.TURN_LEFT
                else -> TurnType.TURN_SHARP_LEFT
            }

            val instructionText = when (turnType) {
                TurnType.ARRIVED -> "Arriving at ${next.name}"
                TurnType.HEAD_STRAIGHT -> "Head straight towards ${next.name}"
                TurnType.TURN_SLIGHT_RIGHT -> "Turn slightly right towards ${next.name}"
                TurnType.TURN_RIGHT -> "Turn right towards ${next.name}"
                TurnType.TURN_SHARP_RIGHT -> "Make a sharp right towards ${next.name}"
                TurnType.TURN_SLIGHT_LEFT -> "Turn slightly left towards ${next.name}"
                TurnType.TURN_LEFT -> "Turn left towards ${next.name}"
                TurnType.TURN_SHARP_LEFT -> "Make a sharp left towards ${next.name}"
            }

            steps.add(NavigationStep(instructionText, stepDist, turnType, next))
            previousBearing = currentBearing
        }
        return steps
    }

    fun distanceToBuildingPolygon(
        userLat: Double,
        userLng: Double,
        boundary: List<GeoNode>,
        centerLat: Double,
        centerLng: Double
    ): Double {
        if (boundary.isEmpty()) {
            return distanceBetween(userLat, userLng, centerLat, centerLng)
        }

        var minDistance = Double.MAX_VALUE
        for (i in 0 until boundary.size) {
            val a = boundary[i]
            val b = boundary[(i + 1) % boundary.size]
            val dist = distanceToSegment(userLat, userLng, a.lat, a.lng, b.lat, b.lng)
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        val centerDist = distanceBetween(userLat, userLng, centerLat, centerLng)
        return minOf(minDistance, centerDist)
    }

    fun checkArrival(
        userLat: Double,
        userLng: Double,
        boundary: List<GeoNode>,
        centerLat: Double,
        centerLng: Double,
        thresholdMeters: Double = 2.0
    ): Boolean {
        val dist = distanceToBuildingPolygon(userLat, userLng, boundary, centerLat, centerLng)
        return dist <= thresholdMeters
    }

    private fun distanceToSegment(pLat: Double, pLng: Double, aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val abDistSq = (bLat - aLat).pow(2) + (bLng - aLng).pow(2)
        if (abDistSq == 0.0) return distanceBetween(pLat, pLng, aLat, aLng)

        val t = ((pLat - aLat) * (bLat - aLat) + (pLng - aLng) * (bLng - aLng)) / abDistSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val projLat = aLat + clampedT * (bLat - aLat)
        val projLng = aLng + clampedT * (bLng - aLng)
        return distanceBetween(pLat, pLng, projLat, projLng)
    }

    private fun projectPointOnSegment(pLat: Double, pLng: Double, aLat: Double, aLng: Double, bLat: Double, bLng: Double): GeoNode {
        val abDistSq = (bLat - aLat).pow(2) + (bLng - aLng).pow(2)
        if (abDistSq == 0.0) return GeoNode("proj", "Projected", aLat, aLng)

        val t = ((pLat - aLat) * (bLat - aLat) + (pLng - aLng) * (bLng - aLng)) / abDistSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val projLat = aLat + clampedT * (bLat - aLat)
        val projLng = aLng + clampedT * (bLng - aLng)
        return GeoNode("proj", "Projected", projLat, projLng)
    }

    fun getActivePath(): List<GeoNode> = activePath

    fun snapToRouteOrNetwork(userLat: Double, userLng: Double, routePath: List<GeoNode>): GeoNode {
        if (routePath.isEmpty()) return GeoNode("user_pos", "Current Location", userLat, userLng)

        var closestPoint: GeoNode = routePath[0]
        var minDistance = Double.MAX_VALUE

        for (i in 0 until routePath.size - 1) {
            val a = routePath[i]
            val b = routePath[i + 1]
            val projected = projectPointOnSegment(userLat, userLng, a.lat, a.lng, b.lat, b.lng)
            val dist = distanceBetween(userLat, userLng, projected.lat, projected.lng)
            if (dist < minDistance) {
                minDistance = dist
                closestPoint = projected
            }
        }

        if (minDistance > 15.0) {
            val nearestNode = findNearestNode(userLat, userLng)
            if (nearestNode != null && distanceBetween(userLat, userLng, nearestNode.lat, nearestNode.lng) < 15.0) {
                return nearestNode
            }
            return GeoNode("user_pos", "Current Location", userLat, userLng)
        }

        return closestPoint
    }
}
