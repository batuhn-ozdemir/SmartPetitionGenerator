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

    @POST("/api/v1/petition/ocr-layout/queue")
    suspend fun enqueueOcrLayout(
        @Header("X-Client-Id") clientId: String,
        @Body request: OcrLayoutRequest
    ): OcrQueueResponse

    @GET("/api/v1/petition/ocr-layout/status/{ticketId}")
    suspend fun checkOcrStatus(
        @Header("X-Client-Id") clientId: String,
        @Path("ticketId") ticketId: String
    ): OcrQueueResponse
}