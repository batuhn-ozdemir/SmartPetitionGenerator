package com.gpproject.smartpetitiongenerator.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RequestSigningInterceptor(
    private val signingSecret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (signingSecret.isBlank()) {
            return chain.proceed(request)
        }

        val clientId = request.header("X-Client-Id") ?: "anonymous"
        val timestamp = (System.currentTimeMillis() / 1000L).toString()

        val payload = "${request.method()}\n${request.url().encodedPath()}\n$timestamp\n$clientId"
        val signature = hmacSha256Hex(signingSecret, payload)

        val signedRequest = request.newBuilder()
            .header("X-Timestamp", timestamp)
            .header("X-Signature", signature)
            .build()

        return chain.proceed(signedRequest)
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)

        val rawHmac = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))

        val sb = StringBuilder(rawHmac.size * 2)
        for (b in rawHmac) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}