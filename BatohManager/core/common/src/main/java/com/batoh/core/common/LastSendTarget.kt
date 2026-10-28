package com.batoh.core.common

import android.content.Context

/** Last individual panel chosen for a send. Availability is checked anew before every send. */
object LastSendTarget {
    private const val PREFS_NAME = "last_send_target"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"

    data class Value(val address: String, val name: String?)

    fun read(context: Context): Value? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        return Value(address, prefs.getString(KEY_NAME, null))
    }

    fun remember(context: Context, address: String, name: String?) {
        if (address.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .apply()
    }
}
