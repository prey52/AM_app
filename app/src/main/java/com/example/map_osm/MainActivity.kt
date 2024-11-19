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
import androidx.compose.ui.tooling.preview.Preview
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfiguracja OSM
        Configuration.getInstance().load(this, this.getSharedPreferences("osm_pref", MODE_PRIVATE))

        Log.d("MainActivity", "Application started.")

        fetchAndCacheTours()
        checkLocationPermission()

        // Wczytaj trasy z pamięci podręcznej
        val cachedTours = readCachedTours()
        if (cachedTours != null) {
            Log.d("Cache", "Loaded ${cachedTours.size} tours from cache.")
        } else {
            Log.d("Cache", "No cached tours found.")
        }

        setContent {
            Map_OSMTheme {
                MainScreen(cachedTours ?: emptyList()) // Przekaż trasy do ekranu głównego
            }
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupMapWithLocation()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("Permissions", "Location permission granted.")
                setupMapWithLocation()
            } else {
                Log.d("Permissions", "Location permission denied.")
            }
        }

    private fun setupMapWithLocation() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (!this@MainActivity::mapView.isInitialized) {
                mapView = MapView(this@MainActivity).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(49.803455, 19.048866)) // Przykładowy punkt
                }
            }

            // Dodaj lokalizację użytkownika
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this@MainActivity), mapView).apply {
                enableMyLocation() // Włącz aktualizację lokalizacji
                enableFollowLocation() // Kamera będzie podążać za użytkownikiem
            }

            mapView.overlays.add(locationOverlay)
        }
    }

    private fun fetchAndCacheTours() {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("Network", "Fetching tours from the REST API...")
            try {
                val response = ApiClient.service.getTours().execute()
                if (response.isSuccessful) {
                    val tours = response.body() ?: emptyList()
                    Log.d("Network", "Tours fetched successfully: ${tours.size} tours retrieved.")

                    val cacheFile = File(cacheDir, "tours_cache.json")
                    cacheFile.writeText(Gson().toJson(tours))
                    Log.d("Cache", "Tours cached successfully at ${cacheFile.absolutePath}.")
                } else {
                    Log.e("Network", "Error fetching tours: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("Network", "Exception occurred while fetching tours: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun readCachedTours(): List<Tour>? {
        val cacheFile = File(cacheDir, "tours_cache.json")
        return if (cacheFile.exists()) {
            val cachedData = cacheFile.readText()
            Log.d("Cache", "Cached data read successfully: $cachedData")
            Gson().fromJson(cachedData, Array<Tour>::class.java).toList()
        } else {
            Log.d("Cache", "No cache file found at ${cacheFile.absolutePath}.")
            null
        }
    }
}

@Composable
fun MainScreen(tours: List<Tour>) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa OSM
        AndroidView(
            factory = {
                MapView(context).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(49.803455, 19.048866)) // Przykładowy punkt
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Przycisk na mapie
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Button(onClick = { showDialog = true }) {
                Text(text = "Wybierz trasę")
            }

            // Okienko dialogowe
            if (showDialog) {
                MyDialog(onDismiss = { showDialog = false }, tours = tours)
            }
        }
    }
}

@Composable
fun MyDialog(onDismiss: () -> Unit, tours: List<Tour>) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Trasy") },
        text = {
            Column {
                tours.forEach { tour ->
                    Button(
                        onClick = {
                            Log.d("Dialog", "Wybrano trasę: ${tour.name}")
                            // Możesz dodać logikę dla wybranej trasy
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
    val dummyTours = listOf(
        Tour(1, "Centra handlowe", emptyList()),
        Tour(2, "UBB główne budynki", emptyList()),
        Tour(3, "UBB pobliskie żabki", emptyList())
    )
    Map_OSMTheme {
        MainScreen(dummyTours)
    }
}