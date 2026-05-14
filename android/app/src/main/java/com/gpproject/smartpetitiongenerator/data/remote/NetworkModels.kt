package com.gpproject.smartpetitiongenerator.data.remote

import com.google.gson.annotations.SerializedName

// Request body sent when the user asks AI to generate a petition.
data class UserPrompt(
    @SerializedName("text")
    val text: String
)

// General response model for AI petition generation requests.
data class AiResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("ticketId")
    val ticketId: String?,

    @SerializedName("payload")
    val payload: String?
)

// Parsed AI result containing the generated template and extracted parameters.
data class AiGeneratedPetition(
    @SerializedName("templateHtml")
    val templateHtml: String?,

    @SerializedName("givenParams")
    val givenParams: Map<String, String>?,

    @SerializedName("requiredParams")
    val requiredParams: List<InputField>?
)

// Represents a single input field required to complete a petition template.
data class InputField(
    @SerializedName("key")
    val key: String,

    @SerializedName("label")
    val label: String,

    @SerializedName("type")
    val type: String
)

// Request body used for sending an image to the OCR layout service.
data class OcrLayoutRequest(
    @SerializedName("imageBase64")
    val imageBase64: String,

    @SerializedName("mimeType")
    val mimeType: String
)

// OCR layout result returned by the backend.
data class OcrLayoutResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("errorCode")
    val errorCode: String?,

    @SerializedName("errorMessage")
    val errorMessage: String?,

    @SerializedName("confidence")
    val confidence: Double,

    @SerializedName("petition")
    val petition: Boolean,

    @SerializedName("imageWidth")
    val imageWidth: Int,

    @SerializedName("imageHeight")
    val imageHeight: Int,

    @SerializedName("lines")
    val lines: List<OcrLine>
)

// Represents a detected text line with its position on the original image.
data class OcrLine(
    @SerializedName("text")
    val text: String,

    @SerializedName("leftPx")
    val leftPx: Int,

    @SerializedName("topPx")
    val topPx: Int,

    @SerializedName("widthPx")
    val widthPx: Int,

    @SerializedName("heightPx")
    val heightPx: Int,

    @SerializedName("writingType")
    val writingType: String?
)

// Queue response used while the OCR operation is still processing or completed.
data class OcrQueueResponse(
    val status: String,
    val ticketId: String?,
    val payload: OcrLayoutResponse?,
    val message: String?
)