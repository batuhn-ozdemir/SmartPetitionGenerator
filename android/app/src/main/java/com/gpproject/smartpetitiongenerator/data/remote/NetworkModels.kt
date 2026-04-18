package com.gpproject.smartpetitiongenerator.data.remote

import com.google.gson.annotations.SerializedName

data class UserPrompt(
    @SerializedName("text") val text: String
)

data class AiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("ticketId") val ticketId: String?,
    @SerializedName("payload") val payload: String?
)

data class AiGeneratedPetition(
    @SerializedName("templateHtml") val templateHtml: String?,
    @SerializedName("givenParams") val givenParams: Map<String, String>?,
    @SerializedName("requiredParams") val requiredParams: List<InputField>?
)

data class InputField(
    @SerializedName("key") val key: String,
    @SerializedName("label") val label: String,
    @SerializedName("type") val type: String
)

data class OcrLayoutRequest(
    @SerializedName("imageBase64") val imageBase64: String,
    @SerializedName("mimeType") val mimeType: String
)

data class OcrLayoutResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("errorCode") val errorCode: String?,
    @SerializedName("errorMessage") val errorMessage: String?,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("petition") val petition: Boolean,
    @SerializedName("imageWidth") val imageWidth: Int,
    @SerializedName("imageHeight") val imageHeight: Int,
    @SerializedName("lines") val lines: List<OcrLine>
)

data class OcrLine(
    @SerializedName("text") val text: String,
    @SerializedName("leftPx") val leftPx: Int,
    @SerializedName("topPx") val topPx: Int,
    @SerializedName("widthPx") val widthPx: Int,
    @SerializedName("heightPx") val heightPx: Int,
    @SerializedName("writingType") val writingType: String?
)

data class OcrQueueResponse(
    val status: String,
    val ticketId: String?,
    val payload: OcrLayoutResponse?,
    val message: String?
)