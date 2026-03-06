package com.gpproject.smartpetitiongenerator.data.remote

import android.content.Context
import java.util.UUID

object ClientIdProvider {

    private const val PREF = "smart_petition_prefs"
    private const val KEY = "client_id"

    fun getClientId(context: Context): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        sp.edit().putString(KEY, newId).apply()
        return newId
    }
}