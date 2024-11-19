package com.example.map_osm
import retrofit2.Call
import retrofit2.http.GET

interface TourApiService {
    @GET("/api/routes/")
    fun getTours(): Call<List<Tour>>
}

data class Tour(
    val id: Int,
    val name: String,
    val waypoints: List<Waypoint>
)

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val description: String
)