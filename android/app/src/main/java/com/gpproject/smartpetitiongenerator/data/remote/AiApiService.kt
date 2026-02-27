package com.gpproject.smartpetitiongenerator.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApiService {

    @POST("/api/v1/petition/generate")
    suspend fun sendPrompt(
        @Header("X-Client-Id") clientId: String,
        @Body prompt: UserPrompt
    ): AiResponse

    @GET("/api/v1/petition/status/{ticketId}")
    suspend fun checkStatus(
        @Header("X-Client-Id") clientId: String,
        @Path("ticketId") ticketId: String
    ): AiResponse
}