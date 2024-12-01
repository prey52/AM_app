package com.example.map_osm

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // private const val BASE_URL = "http://192.168.1.2:5098" //PC
    private const val BASE_URL = "http://172.18.34.107:5098" //VM

    val service: TourApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TourApiService::class.java)
    }
}