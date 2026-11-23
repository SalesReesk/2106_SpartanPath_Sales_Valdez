package com.example.araspoint_map.frontend.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.araspoint_map.backend.model.Building
import com.example.araspoint_map.backend.model.GeoNode
import com.example.araspoint_map.backend.navigation.BuildingPolygon
import com.example.araspoint_map.backend.navigation.CampusNavigationEngine
import com.example.araspoint_map.backend.navigation.KmlParser
import com.example.araspoint_map.backend.navigation.ParsedCampusData
import com.example.araspoint_map.databinding.ActivityMapBinding
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private var locationManager: LocationManager? = null

    private var parsedCampusData: ParsedCampusData? = null
    private var navigationEngine: CampusNavigationEngine? = null

    private var activeDestinationBuildingId: String? = null
    private var activeDestinationBuildingName: String = ""
    private var activeBuildingPolygon: BuildingPolygon? = null
    private var activeEntranceNode: GeoNode? = null
    private var hasArrived: Boolean = false

    private var lastKnownLocation: Location? = null

    // --- Dummy GPS Simulator Properties ---
    private var isDummyGpsEnabled: Boolean = false
    private var dummyLat: Double = 14.067698
    private var dummyLng: Double = 120.627504
    private val autoWalkHandler = Handler(Looper.getMainLooper())
    private var autoWalkRunnable: Runnable? = null
    private var isAutoWalking: Boolean = false

    private val campusBuildings = listOf(
        Building("HEB", "HEB Building", "HEB", 14.067322, 120.626777, "Higher Education Building", "General Academics"),
        Building("CTEB", "CTEB Building", "CTEB", 14.066589, 120.626364, "College of Teacher Education Building", "CTE"),
        Building("GYM", "JOSON GYMNASIUM", "GYM", 14.067103, 120.626169, "University Athletics & Event Gym", "Sports"),
        Building("TAAL", "TAAL Building", "TAAL", 14.067332, 120.625627, "Academic Building Taal", "General"),
        Building("LEMERY", "LEMERY Building", "LEMERY", 14.065859, 120.626151, "Academic Building Lemery", "General"),
        Building("IBAAN", "IBAAN Building", "IBAAN", 14.067655, 120.625926, "Academic Building Ibaan", "General"),
        Building("CAS", "CAS BUILDING", "CAS", 14.066022, 120.626146, "College of Arts and Sciences", "CAS"),
        Building("HOSTEL", "NASUGBU HOSTEL", "HOSTEL", 14.066962, 120.627023, "Campus Guest Hostel & Lodging", "Services"),
        Building("IMT", "IMT Building", "IMT", 14.066387, 120.625581, "Industrial Materials & Technology", "CIT"),
        Building("LIAN", "LIAN Cafeteria", "LIAN", 14.066721, 120.626725, "Campus Student Cafeteria", "Services"),
        Building("ROSARIO", "ROSARIO Canteen", "ROSARIO", 14.067319, 120.625961, "Student Refreshment Canteen", "Services"),
        Building("INFIRMARY", "INFIRMARY", "INFIRMARY", 14.067080, 120.625864, "Campus Health Services & Clinic", "CHS"),
        Building("APACIBLE", "APACIBLE MUSEUM", "APACIBLE", 14.067366, 120.626187, "Historical Museum & Exhibits", "Culture"),
        Building("ERMITA", "ERMITA BUILDING", "ERMITA", 14.067032, 120.625615, "Ermita Academic Building", "General")
    )

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineGranted || coarseGranted) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied. Map showing campus view.", Toast.LENGTH_SHORT).show()
            }
        }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onBuildingClicked(buildingId: String) {
            runOnUiThread {
                val b = campusBuildings.find { it.id == buildingId }
                val title = b?.name ?: buildingId
                val subtitle = if (b != null) "${b.getFormattedMapTag()} • ${b.description}" else "Campus Facility"
                val sheet = BuildingAssetsBottomSheetDialogFragment(
                    buildingTitle = title,
                    buildingSubtitle = subtitle,
                    onNavigateClickListener = {
                        startNavigationToBuilding(buildingId)
                    }
                )
                sheet.show(supportFragmentManager, "BuildingAssetsSheet")
            }
        }

        @JavascriptInterface
        fun onMapTapped(lat: Double, lng: Double) {
            runOnUiThread {
                if (isDummyGpsEnabled) {
                    stopAutoWalk()
                    updateDummyLocation(lat, lng)
                    Toast.makeText(this@MapActivity, "GPS set to map tap", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBackMap.setOnClickListener {
            finish()
        }

        binding.btnStopNavigation.setOnClickListener {
            stopNavigation()
        }

        binding.btnFocusTrip.setOnClickListener {
            val uLat = lastKnownLocation?.latitude ?: dummyLat
            val uLng = lastKnownLocation?.longitude ?: dummyLng
            focusCameraOnLocation(uLat, uLng)
        }

        setupGpsSimulatorUi()
        loadCampusKmlGraph()
        setupLeafletWebView()
        checkAndRequestLocationPermissions()
    }

    private fun focusCameraOnLocation(lat: Double, lng: Double) {
        binding.mapWebView.evaluateJavascript("map.setView([$lat, $lng], 20.5, { animate: true })", null)
        Toast.makeText(this, "Trip camera focused", Toast.LENGTH_SHORT).show()
    }

    private fun setupGpsSimulatorUi() {
        binding.btnToggleGpsSimulator.setOnClickListener {
            if (binding.cardGpsSimulator.visibility == View.VISIBLE) {
                binding.cardGpsSimulator.visibility = View.GONE
                isDummyGpsEnabled = false
                stopAutoWalk()
                Toast.makeText(this, "Dummy GPS disabled", Toast.LENGTH_SHORT).show()
            } else {
                binding.cardGpsSimulator.visibility = View.VISIBLE
                isDummyGpsEnabled = true
                updateDummyLocation(dummyLat, dummyLng)
                Toast.makeText(this, "Dummy GPS enabled. Tap map to set location.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCloseGpsSim.setOnClickListener {
            binding.cardGpsSimulator.visibility = View.GONE
            isDummyGpsEnabled = false
            stopAutoWalk()
        }

        val stepDelta = 0.000025 // ~2.7 meters per tap
        binding.btnGpsNorth.setOnClickListener { nudgeDummyGps(stepDelta, 0.0) }
        binding.btnGpsSouth.setOnClickListener { nudgeDummyGps(-stepDelta, 0.0) }
        binding.btnGpsEast.setOnClickListener { nudgeDummyGps(0.0, stepDelta) }
        binding.btnGpsWest.setOnClickListener { nudgeDummyGps(0.0, -stepDelta) }

        binding.btnGpsAutoWalk.setOnClickListener {
            if (!isDummyGpsEnabled) {
                binding.btnToggleGpsSimulator.performClick()
            }
            toggleAutoWalk()
        }

        binding.btnGpsTeleportNearTarget.setOnClickListener {
            if (!isDummyGpsEnabled) {
                isDummyGpsEnabled = true
                binding.cardGpsSimulator.visibility = View.VISIBLE
            }
            teleportNearTargetBuilding()
        }
    }

    private fun nudgeDummyGps(dLat: Double, dLng: Double) {
        stopAutoWalk()
        dummyLat += dLat
        dummyLng += dLng
        updateDummyLocation(dummyLat, dummyLng)
    }

    private fun updateDummyLocation(lat: Double, lng: Double) {
        val engine = navigationEngine
        val effectiveCoords = if (engine != null && activeDestinationBuildingId != null) {
            val snapped = engine.snapToRouteOrNetwork(lat, lng, engine.getActivePath())
            Pair(snapped.lat, snapped.lng)
        } else {
            Pair(lat, lng)
        }

        dummyLat = effectiveCoords.first
        dummyLng = effectiveCoords.second

        val mockLocation = Location("dummy_gps").apply {
            latitude = effectiveCoords.first
            longitude = effectiveCoords.second
            accuracy = 1.0f
            time = System.currentTimeMillis()
        }
        lastKnownLocation = mockLocation

        binding.txtDummyGpsCoords.text = "Pos: %.6f, %.6f".format(effectiveCoords.first, effectiveCoords.second)
        binding.mapWebView.evaluateJavascript("updateUserMarker(${effectiveCoords.first}, ${effectiveCoords.second}, ${activeDestinationBuildingId != null})", null)

        if (activeDestinationBuildingId != null) {
            updateNavigationRoute()
        }
    }

    private fun toggleAutoWalk() {
        if (isAutoWalking) {
            stopAutoWalk()
            Toast.makeText(this, "Auto-walk paused", Toast.LENGTH_SHORT).show()
        } else {
            val destinationId = activeDestinationBuildingId
            if (destinationId == null) {
                Toast.makeText(this, "Please select a building to navigate first!", Toast.LENGTH_SHORT).show()
                return
            }
            startAutoWalk()
        }
    }

    private fun startAutoWalk() {
        val engine = navigationEngine ?: return
        val entranceNode = activeEntranceNode ?: return
        val fullPath = engine.findShortestPathFromLocation(dummyLat, dummyLng, entranceNode.id)

        if (fullPath.size < 2) {
            Toast.makeText(this, "Already at or near destination", Toast.LENGTH_SHORT).show()
            return
        }

        isAutoWalking = true
        binding.btnGpsAutoWalk.text = "⏸️ Pause Walk"
        var currentStepIdx = 1

        autoWalkRunnable = object : Runnable {
            override fun run() {
                if (!isAutoWalking || currentStepIdx >= fullPath.size || hasArrived) {
                    stopAutoWalk()
                    return
                }
                val targetNode = fullPath[currentStepIdx]
                updateDummyLocation(targetNode.lat, targetNode.lng)
                currentStepIdx++

                if (currentStepIdx < fullPath.size && !hasArrived) {
                    autoWalkHandler.postDelayed(this, 1200L)
                } else {
                    stopAutoWalk()
                }
            }
        }
        autoWalkHandler.postDelayed(autoWalkRunnable!!, 500L)
    }

    private fun stopAutoWalk() {
        isAutoWalking = false
        autoWalkRunnable?.let { autoWalkHandler.removeCallbacks(it) }
        autoWalkRunnable = null
        binding.btnGpsAutoWalk.text = "🚶 Walk Route"
    }

    private fun teleportNearTargetBuilding() {
        stopAutoWalk()
        val entranceNode = activeEntranceNode
        val buildingPoly = activeBuildingPolygon

        val targetLat: Double
        val targetLng: Double

        if (buildingPoly != null && buildingPoly.boundary.isNotEmpty()) {
            val pt = buildingPoly.boundary[0]
            targetLat = pt.lat
            targetLng = pt.lng
        } else if (entranceNode != null) {
            targetLat = entranceNode.lat
            targetLng = entranceNode.lng
        } else {
            Toast.makeText(this, "No active target building selected", Toast.LENGTH_SHORT).show()
            return
        }

        updateDummyLocation(targetLat, targetLng)
        Toast.makeText(this, "Teleported near destination (< 2m)", Toast.LENGTH_SHORT).show()
    }

    private fun loadCampusKmlGraph() {
        try {
            val data = KmlParser.parseKml(this, "ArasPointUpdated.kml")
            parsedCampusData = data
            navigationEngine = CampusNavigationEngine(data.nodes, data.adjacencyList)
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackNodes = listOf(
                GeoNode("fallback_1", "Main Walk", 14.067698, 120.627504),
                GeoNode("fallback_2", "HEB Area", 14.067322, 120.626777)
            )
            val fallbackAdj = mapOf("fallback_1" to listOf(fallbackNodes[1]), "fallback_2" to listOf(fallbackNodes[0]))
            navigationEngine = CampusNavigationEngine(fallbackNodes, fallbackAdj)
            parsedCampusData = ParsedCampusData(emptyList(), emptyList(), fallbackNodes, fallbackAdj, emptyMap())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLeafletWebView() {
        binding.mapWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }
        binding.mapWebView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")
        binding.mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Keep background walkpaths hidden by default until a route trip starts
            }
        }
        binding.mapWebView.loadUrl("file:///android_asset/campus_map.html")

        binding.btnRecenterCampus.setOnClickListener {
            binding.mapWebView.evaluateJavascript("recenterCampus()", null)
            Toast.makeText(this, "Centered on campus", Toast.LENGTH_SHORT).show()
        }

        binding.btnZoomIn.setOnClickListener {
            binding.mapWebView.evaluateJavascript("zoomIn()", null)
        }

        binding.btnZoomOut.setOnClickListener {
            binding.mapWebView.evaluateJavascript("zoomOut()", null)
        }
    }

    fun startNavigationToBuilding(buildingId: String) {
        stopAutoWalk()
        val engine = navigationEngine ?: return
        val data = parsedCampusData ?: return

        val building = campusBuildings.find { it.id == buildingId } ?: return
        val buildingPoly = data.buildings.find { it.id == buildingId }
        val entranceNode = data.buildingToEntranceNode[buildingId] ?: engine.findNearestNode(building.centerLat, building.centerLng)

        if (entranceNode == null) {
            Toast.makeText(this, "Could not locate entrance node for ${building.name}", Toast.LENGTH_SHORT).show()
            return
        }

        activeDestinationBuildingId = buildingId
        activeDestinationBuildingName = building.name
        activeBuildingPolygon = buildingPoly
        activeEntranceNode = entranceNode
        hasArrived = false

        binding.cardNavigationBanner.visibility = View.VISIBLE
        binding.btnFocusTrip.visibility = View.VISIBLE

        binding.txtNavBuildingTitle.text = "Navigating to ${building.name}"
        binding.txtNavBuildingTitle.setTextColor(0xFFFFFFFF.toInt())

        updateNavigationRoute(isInitialStart = true)
    }

    private fun updateNavigationRoute(isInitialStart: Boolean = false) {
        try {
            val engine = navigationEngine ?: return
            val userLat = lastKnownLocation?.latitude ?: dummyLat
            val userLng = lastKnownLocation?.longitude ?: dummyLng
            val entranceNode = activeEntranceNode ?: engine.findNearestNode(userLat, userLng) ?: return
            val buildingPoly = activeBuildingPolygon

            val bldgLat = buildingPoly?.centerLat ?: entranceNode.lat
            val bldgLng = buildingPoly?.centerLng ?: entranceNode.lng
            val boundary = buildingPoly?.boundary ?: emptyList()

            // 2-meter arrival detection
            val distToBuilding = engine.distanceToBuildingPolygon(userLat, userLng, boundary, bldgLat, bldgLng)

            if (distToBuilding <= 2.0) {
                if (!hasArrived) {
                    hasArrived = true
                    onArrivedAtBuilding(activeDestinationBuildingName)
                }
                return
            }

            // Calculate shortest path (guaranteed non-empty fallback)
            val shortestPath = engine.findShortestPathFromLocation(userLat, userLng, entranceNode.id)
            if (shortestPath.isEmpty()) {
                binding.txtNavInstructionText.text = "Path unavailable to destination"
                return
            }

            // Draw shortest path polyline in WebView & Zoom in close to User GPS
            val jsonArray = JSONArray()
            for (node in shortestPath) {
                val obj = JSONObject()
                obj.put("lat", node.lat)
                obj.put("lng", node.lng)
                jsonArray.put(obj)
            }
            val safeName = JSONObject.quote(activeDestinationBuildingName)
            val jsonArrayStr = JSONObject.quote(jsonArray.toString())
            binding.mapWebView.evaluateJavascript("drawShortestPath($jsonArrayStr, $safeName, $isInitialStart)", null)

            // Draw alternative less efficient walk route (low opacity)
            val altPath = parsedCampusData?.paths?.getOrNull(1)?.coordinates ?: emptyList()
            if (altPath.isNotEmpty()) {
                val altJsonArray = JSONArray()
                for (node in altPath) {
                    val obj = JSONObject()
                    obj.put("lat", node.lat)
                    obj.put("lng", node.lng)
                    altJsonArray.put(obj)
                }
                val altJsonArrayStr = JSONObject.quote(altJsonArray.toString())
                binding.mapWebView.evaluateJavascript("drawAlternativeRoute($altJsonArrayStr)", null)
            }

            // Turn instructions
            val steps = engine.generateTurnInstructions(shortestPath)

            val totalDistanceMeters = steps.sumOf { it.distanceMeters }.roundToInt()
            val firstStep = steps.firstOrNull()?.instruction ?: "Head towards destination"

            binding.txtNavInstructionText.text = "Distance: ${totalDistanceMeters}m • $firstStep"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onArrivedAtBuilding(buildingName: String) {
        binding.txtNavBuildingTitle.text = "🎯 Arrived at $buildingName!"
        binding.txtNavBuildingTitle.setTextColor(0xFF22C55E.toInt())
        binding.txtNavInstructionText.text = "You are within 2 meters of the building area."

        val safeName = JSONObject.quote(buildingName)
        binding.mapWebView.evaluateJavascript("showArrivalAlert($safeName)", null)
        Toast.makeText(this, "You have arrived at $buildingName!", Toast.LENGTH_LONG).show()
    }

    private fun stopNavigation() {
        stopAutoWalk()
        activeDestinationBuildingId = null
        activeDestinationBuildingName = ""
        activeBuildingPolygon = null
        activeEntranceNode = null
        hasArrived = false

        binding.cardNavigationBanner.visibility = View.GONE
        binding.btnFocusTrip.visibility = View.GONE

        binding.mapWebView.evaluateJavascript("clearShortestPath()", null)
        Toast.makeText(this, "Navigation cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestLocationPermissions() {
        val finePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (finePermission == PackageManager.PERMISSION_GRANTED || coarsePermission == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isDummyGpsEnabled) {
                        val engine = navigationEngine
                        val effectiveLoc = if (engine != null && activeDestinationBuildingId != null) {
                            val snapped = engine.snapToRouteOrNetwork(location.latitude, location.longitude, engine.getActivePath())
                            Location(location).apply {
                                latitude = snapped.lat
                                longitude = snapped.lng
                            }
                        } else {
                            location
                        }

                        lastKnownLocation = effectiveLoc
                        binding.mapWebView.evaluateJavascript(
                            "updateUserMarker(${effectiveLoc.latitude}, ${effectiveLoc.longitude}, ${activeDestinationBuildingId != null})",
                            null
                        )
                        if (activeDestinationBuildingId != null) {
                            updateNavigationRoute()
                        }
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, locationListener)
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1f, locationListener)
            }

            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null && !isDummyGpsEnabled) {
                lastKnownLocation = lastKnown
                binding.mapWebView.evaluateJavascript(
                    "updateUserMarker(${lastKnown.latitude}, ${lastKnown.longitude}, ${activeDestinationBuildingId != null})",
                    null
                )
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoWalk()
        locationManager = null
    }
}
