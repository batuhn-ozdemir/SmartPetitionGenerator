package com.gpproject.smartpetitiongenerator.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApiService {

    // Sends the user's prompt to the backend for AI-based petition generation.
    @POST("/api/v1/petition/generate")
    suspend fun sendPrompt(
        @Header("X-Client-Id") clientId: String,
        @Body prompt: UserPrompt
    ): AiResponse

    // Checks the current status of a previously queued AI petition request.
    @GET("/api/v1/petition/status/{ticketId}")
    suspend fun checkStatus(
        @Header("X-Client-Id") clientId: String,
        @Path("ticketId") ticketId: String
    ): AiResponse

    // Sends an image to the backend and queues an OCR layout extraction request.
    @POST("/api/v1/petition/ocr-layout/queue")
    suspend fun enqueueOcrLayout(
        @Header("X-Client-Id") clientId: String,
        @Body request: OcrLayoutRequest
    ): OcrQueueResponse

    // Checks the current status of a previously queued OCR layout request.
    @GET("/api/v1/petition/ocr-layout/status/{ticketId}")
    suspend fun checkOcrStatus(
        @Header("X-Client-Id") clientId: String,
        @Path("ticketId") ticketId: String
    ): OcrQueueResponse
}