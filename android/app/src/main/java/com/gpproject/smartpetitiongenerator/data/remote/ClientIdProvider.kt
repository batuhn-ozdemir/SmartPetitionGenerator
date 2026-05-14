package com.gpproject.smartpetitiongenerator.data.remote

import android.content.Context
import java.util.UUID

object ClientIdProvider {

    private const val PREF = "smart_petition_prefs"
    private const val KEY = "client_id"

    // Returns a persistent unique client ID for this device/app installation.
    fun getClientId(context: Context): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // Reuse the existing client ID if it was already generated before.
        val existing = sp.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing

        // Generate and store a new client ID for future requests.
        val newId = UUID.randomUUID().toString()
        sp.edit().putString(KEY, newId).apply()

        return newId
    }
}