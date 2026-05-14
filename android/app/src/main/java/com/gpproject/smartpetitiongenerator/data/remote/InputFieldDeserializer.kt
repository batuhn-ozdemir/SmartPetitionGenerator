package com.gpproject.smartpetitiongenerator.data.remote

import com.google.gson.*
import java.lang.reflect.Type

class InputFieldDeserializer : JsonDeserializer<InputField> {

    // Converts different requiredParams formats into a common InputField model.
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): InputField {

        // Handles simple string values such as: "DONEM" or "KURUM_ADI".
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val key = json.asString

            return InputField(
                key = key,
                label = key,
                type = "text"
            )
        }

        // Handles normal object values such as: { "key": "...", "label": "...", "type": "..." }.
        val obj = json.asJsonObject

        val key = obj.get("key")?.asString ?: "UNKNOWN"
        val label = obj.get("label")?.asString ?: key
        val type = obj.get("type")?.asString ?: "text"

        return InputField(
            key = key,
            label = label,
            type = type
        )
    }
}