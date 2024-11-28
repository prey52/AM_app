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


class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

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
                MainScreen(mapView, tours, ::addWaypointsToMap)  // Przekazanie funkcji
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
        // Clear existing overlays
        mapView.overlays.clear()

        // Plot markers for each waypoint
        val waypoints = tour.waypoints.map { GeoPoint(it.latitude, it.longitude) }
        waypoints.forEach { waypoint ->
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

        // Add the location overlay back
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }

        mapView.invalidate()
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
            }
        }

    private fun setupMapWithLocation() {
        lifecycleScope.launch(Dispatchers.Main) {
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this@MainActivity), mapView).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapView.overlays.add(locationOverlay)
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

