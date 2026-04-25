package com.gpproject.smartpetitiongenerator.data.remote

import com.gpproject.smartpetitiongenerator.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Android Emulator için localhost IP'si: 10.0.2.2
    // Gerçek cihazda test edeceksen bilgisayarının yerel IP'sini yazmalısın (örn: 192.168.1.35)
    //private const val BASE_URL = "http://10.0.2.2:8080/"
    private const val BASE_URL = BuildConfig.BASE_URL

    val apiService: AiApiService by lazy {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .addInterceptor(RequestSigningInterceptor(BuildConfig.APP_SIGNING_SECRET))
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }
}