package com.example.map_osm

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.map_osm.ui.theme.Map_OSMTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import androidx.compose.ui.tooling.preview.Preview
import org.osmdroid.views.overlay.Marker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    private var currentWaypointIndex = 0
    private var currentTour: Tour? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, this.getSharedPreferences("osm_pref", MODE_PRIVATE))
        mapView = MapView(this).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(49.803455, 19.048866))
        }

        checkLocationPermission()

        fetchAndCacheTours { tours ->
            if (tours.isEmpty()) {
                val cachedTours = readCachedTours()
                if (cachedTours != null && cachedTours.isNotEmpty()) {
                    renderMainScreen(cachedTours)
                } else {
                    renderMainScreen(emptyList())
                }
            } else {
                renderMainScreen(tours)
            }
        }
    }

    private fun startTour(tour: Tour) {
        currentTour = tour
        currentWaypointIndex = 0
        navigateToNextWaypoint()
    }

    private fun navigateToNextWaypoint() {
        val tour = currentTour ?: return // Return if tour is null

        if (currentWaypointIndex >= tour.waypoints.size) {
            Log.d("Navigation", "Tour completed!")
            return
        }

        val currentWaypoint = tour.waypoints.getOrNull(currentWaypointIndex) // Safely get the waypoint
        if (currentWaypoint == null) {
            Log.e("Navigation", "Invalid waypoint index: $currentWaypointIndex")
            return
        }

        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            val startPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
            val endPoint = GeoPoint(currentWaypoint.latitude, currentWaypoint.longitude)

            // Clear existing overlays
            mapView.overlays.clear()

            // Add a marker for the waypoint
            val marker = Marker(mapView).apply {
                position = endPoint
                title = currentWaypoint.description ?: "Waypoint"
            }
            mapView.overlays.add(marker)

            // Fetch and display the route
            fetchRoute(startPoint, endPoint) { route ->
                addRouteToMap(route)
            }

            // Keep the location overlay on the map
            if (!mapView.overlays.contains(locationOverlay)) {
                mapView.overlays.add(locationOverlay)
            }

            mapView.invalidate()

            // Monitor user proximity to the waypoint
            monitorProximityToWaypoint(currentWaypoint)
        } else {
            Log.e("LocationError", "Unable to fetch user's location")
        }
    }


    private fun monitorProximityToWaypoint(waypoint: Waypoint) {
        val proximityRadiusMeters = 50.0
        lifecycleScope.launch {
            while (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val userLocation = locationOverlay.myLocation
                if (userLocation != null) {
                    val userPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                    val waypointPoint = GeoPoint(waypoint.latitude, waypoint.longitude)

                    val distance = userPoint.distanceToAsDouble(waypointPoint)
                    Log.d("ProximityDebug", "Distance to waypoint: $distance meters")
                    if (distance <= proximityRadiusMeters) {
                        Log.d("ProximityDebug", "Within proximity radius.")
                        showWaypointReachedDialog(waypoint)
                        break // Exit the loop when the condition is met
                    }
                } else {
                    Log.e("ProximityDebug", "User location is null. Retrying...")
                }
                kotlinx.coroutines.delay(2000) // Retry after 2 seconds
            }
        }
    }





    private fun showWaypointReachedDialog(waypoint: Waypoint) {
        Log.d("DialogDebug", "Attempting to show dialog for waypoint: ${waypoint.description}")
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("You've reached ${waypoint.description ?: "Waypoint"}")
                .setMessage("Proceeding to the next point...")
                .setPositiveButton("OK") { _, _ ->
                    Log.d("DialogDebug", "User confirmed waypoint dialog.")
                    moveToNextWaypoint()
                }
                .show()
        }
    }




    private fun moveToNextWaypoint() {
        val tour = currentTour ?: return // Ensure the tour is not null
        if (currentWaypointIndex < tour.waypoints.size - 1) {
            currentWaypointIndex++
            navigateToNextWaypoint()
        } else {
            Log.d("Navigation", "All waypoints completed.")
        }
    }

    private fun fetchRoute(start: GeoPoint, end: GeoPoint, onRouteFetched: (List<GeoPoint>) -> Unit) {
        val startCoord = "${start.longitude},${start.latitude}"
        val endCoord = "${end.longitude},${end.latitude}"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = OpenRouteServiceClient.service.getRoute(
                    apiKey = "5b3ce3597851110001cf6248073ab13db5f744daaf6f53d2900363c4", // Replace with your ORS API key
                    start = startCoord,
                    end = endCoord
                ).execute()

                if (response.isSuccessful) {
                    val routeCoordinates = response.body()?.features?.firstOrNull()?.geometry?.coordinates
                    if (routeCoordinates != null) {
                        val geoPoints = routeCoordinates.map { GeoPoint(it[1], it[0]) } // Convert to GeoPoint
                        launch(Dispatchers.Main) {
                            onRouteFetched(geoPoints)
                        }
                    }
                } else {
                    Log.e("ORS", "Failed to fetch route: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ORS", "Error fetching route: ${e.message}")
            }
        }
    }



    private fun addRouteToMap(routePoints: List<GeoPoint>) {
        val polyline = org.osmdroid.views.overlay.Polyline().apply {
            setPoints(routePoints)
            color = android.graphics.Color.BLUE
            width = 5f
        }
        mapView.overlays.add(polyline)
        mapView.invalidate()
    }



    private fun renderMainScreen(tours: List<Tour>) {
        setContent {
            Map_OSMTheme {
                MainScreen(mapView, tours) { tour ->
                    startTour(tour)
                }
            }
        }
    }


    private fun fetchAndCacheTours(onDataLoaded: (List<Tour>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.service.getTours().execute()
                if (response.isSuccessful) {
                    val tours = response.body() ?: emptyList()
                    val cacheFile = File(cacheDir, "tours_cache.json")
                    cacheFile.writeText(Gson().toJson(tours))
                    launch(Dispatchers.Main) { onDataLoaded(tours) }
                } else {
                    launch(Dispatchers.Main) { onDataLoaded(emptyList()) }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onDataLoaded(emptyList()) }
            }
        }
    }

    private fun readCachedTours(): List<Tour>? {
        val cacheFile = File(cacheDir, "tours_cache.json")
        return if (cacheFile.exists()) {
            val cachedData = cacheFile.readText()
            Gson().fromJson(cachedData, Array<Tour>::class.java).toList()
        } else {
            null
        }
    }


    private fun addWaypointsToMap(tour: Tour) {
        // Clear existing overlays (except for the location overlay, which is already added)
        mapView.overlays.clear()

        // Get the user's current location
        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            // Start the route from the user's current location
            val waypoints = listOf(GeoPoint(userLocation.latitude, userLocation.longitude)) +
                    tour.waypoints.map { GeoPoint(it.latitude, it.longitude) }

            // Plot markers for each waypoint (excluding the user's location, as it already has an arrow)
            waypoints.drop(1).forEach { waypoint ->  // Drop the user's location from the list
                val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = waypoint
                    title = "Waypoint: ${waypoint.latitude}, ${waypoint.longitude}"
                }
                mapView.overlays.add(marker)
            }

            // Fetch and draw routes between consecutive waypoints
            lifecycleScope.launch {
                for (i in 0 until waypoints.size - 1) {
                    fetchRoute(waypoints[i], waypoints[i + 1]) { route ->
                        addRouteToMap(route)
                    }
                }
            }

            // Keep the location overlay (arrow) on the map, ensuring it's not removed
            if (!mapView.overlays.contains(locationOverlay)) {
                mapView.overlays.add(locationOverlay)
            }

            mapView.invalidate()
        } else {
            // Handle case where user's location is unavailable
            Log.e("LocationError", "Unable to fetch user's location")
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupMapWithLocation()
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setupMapWithLocation()
            } else {
                Log.e("Permission", "Location permission denied")
            }
        }


    private fun setupMapWithLocation() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(locationOverlay)

        if (locationOverlay.myLocation == null) {
            Log.e("LocationError", "Location not available. Ensure GPS is enabled.")
        }
    }


}


@Composable
fun MainScreen(
    mapView: MapView,
    tours: List<Tour> = emptyList(),
    onTourSelected: (Tour) -> Unit  // Parametr funkcji, która obsłuży kliknięcie
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Button(onClick = { showDialog = true }) {
                Text(text = "Wybierz trasę")
            }
            if (showDialog) {
                MyDialog(
                    onDismiss = { showDialog = false },
                    tours = tours,
                    onTourSelected = { tour ->
                        // Po kliknięciu na trasę, dodajemy punkty do mapy i zamykamy dialog
                        onTourSelected(tour)  // Wywołanie funkcji przekazanej jako parametr
                        showDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun MyDialog(onDismiss: () -> Unit, tours: List<Tour>, onTourSelected: (Tour) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Trasy") },
        text = {
            Column {
                tours.forEach { tour ->
                    Button(
                        onClick = {
                            Log.d("Dialog", "Wybrano trasę: ${tour.name}")
                            onTourSelected(tour)  // Wywołanie funkcji po kliknięciu
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = tour.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zamknij")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val context = LocalContext.current

    val dummyMapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        }
    }

    Map_OSMTheme {
        MainScreen(dummyMapView, emptyList(), onTourSelected = {})
    }
}
