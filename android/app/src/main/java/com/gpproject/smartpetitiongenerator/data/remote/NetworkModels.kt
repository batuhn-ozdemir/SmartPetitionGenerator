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