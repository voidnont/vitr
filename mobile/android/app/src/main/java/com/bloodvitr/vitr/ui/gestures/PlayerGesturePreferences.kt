package com.bloodvitr.vitr.ui.gestures

import android.content.Context

object PlayerGesturePreferences {
    private const val PREFS = "vitr_player_prefs"
    private const val KEY_ENABLED = "player_gestures_enabled"

    fun enabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
