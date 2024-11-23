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
        mapView = MapView(this).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(49.803455, 19.048866)) // Przykładowa lokalizacja
        }

        checkLocationPermission()

        setContent {
            Map_OSMTheme {
                MainScreen(mapView, listOf(
                    Tour(1, "Centra handlowe", emptyList()),
                    Tour(2, "UBB główne budynki", emptyList()),
                    Tour(3, "UBB pobliskie żabki", emptyList())
                ))
            }
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
                Log.d("Permissions", "Location permission granted.")
                setupMapWithLocation()
            } else {
                Log.d("Permissions", "Location permission denied.")
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
fun MainScreen(mapView: MapView, tours: List<Tour> = emptyList()) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wyświetl mapę OSM
        AndroidView(
            factory = { mapView },
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
                // Wyświetlanie przycisków dla każdej trasy
                tours.forEach { tour ->
                    Button(
                        onClick = {
                            Log.d("Dialog", "Wybrano trasę: ${tour.name}")
                            // Możesz dodać logikę dla wybranej trasy tutaj
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

    // Tworzymy tymczasowy MapView do podglądu
    val dummyMapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        }
    }

    Map_OSMTheme {
        MainScreen(dummyMapView) // Przekazujemy tymczasowy MapView
    }
}
