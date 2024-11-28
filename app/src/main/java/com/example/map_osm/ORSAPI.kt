package com.example.map_osm

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


interface OpenRouteServiceApi {
    @GET("v2/directions/driving-car") // Specify travel mode (e.g., driving-car, cycling, walking)
    fun getRoute(
        @Header("Authorization") apiKey: String,
        @Query("start") start: String, // "lon1,lat1"
        @Query("end") end: String      // "lon2,lat2"
    ): Call<RouteResponse>
}

object OpenRouteServiceClient {
    private const val BASE_URL = "https://api.openrouteservice.org/"

    val service: OpenRouteServiceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouteServiceApi::class.java)
    }
}

data class RouteResponse(
    val features: List<Feature>
) {
    data class Feature(
        val geometry: Geometry
    ) {
        data class Geometry(
            val coordinates: List<List<Double>> // List of [longitude, latitude] points
        )
    }
}
