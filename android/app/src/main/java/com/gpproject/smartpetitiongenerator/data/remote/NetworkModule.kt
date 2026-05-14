package com.gpproject.smartpetitiongenerator.data.remote

import com.gpproject.smartpetitiongenerator.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Base backend URL is read from BuildConfig.
    // For Android Emulator, localhost should be accessed with 10.0.2.2.
    // For a real device, use the computer's local network IP address.
    private const val BASE_URL = BuildConfig.BASE_URL

    // Creates the Retrofit API service only when it is first needed.
    val apiService: AiApiService by lazy {
        val httpClient = OkHttpClient.Builder()
            // Maximum time allowed to establish a connection.
            .connectTimeout(30, TimeUnit.SECONDS)

            // AI and OCR responses can take longer, so read timeout is higher.
            .readTimeout(180, TimeUnit.SECONDS)

            // Maximum time allowed for sending request data.
            .writeTimeout(30, TimeUnit.SECONDS)

            // Maximum total time allowed for the whole HTTP call.
            .callTimeout(210, TimeUnit.SECONDS)

            // Adds request signing headers if APP_SIGNING_SECRET is not blank.
            .addInterceptor(RequestSigningInterceptor(BuildConfig.APP_SIGNING_SECRET))
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)

            // Converts JSON responses into Kotlin data classes using Gson.
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }
}