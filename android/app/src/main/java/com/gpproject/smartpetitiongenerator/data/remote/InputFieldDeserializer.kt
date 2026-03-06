package com.gpproject.smartpetitiongenerator.data.remote

import com.google.gson.*
import java.lang.reflect.Type

class InputFieldDeserializer : JsonDeserializer<InputField> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): InputField {

        // requiredParams: ["DONEM", "KURUM_ADI"] gibi gelirse
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val key = json.asString
            return InputField(
                key = key,
                label = key,
                type = "text"
            )
        }

        // Normal object ise
        val obj = json.asJsonObject
        val key = obj.get("key")?.asString ?: "UNKNOWN"
        val label = obj.get("label")?.asString ?: key
        val type = obj.get("type")?.asString ?: "text"

        return InputField(key = key, label = label, type = type)
    }
}