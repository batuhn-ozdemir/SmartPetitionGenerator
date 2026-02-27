package com.gpproject.smartpetitiongenerator.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    // Android Emulator localhost IP: 10.0.2.2
    private const val BASE_URL = "http://10.0.2.2:8080/"

    val apiService: AiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }
}