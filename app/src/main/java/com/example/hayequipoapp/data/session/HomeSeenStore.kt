package com.example.hayequipoapp.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSeenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_seen", Context.MODE_PRIVATE)

    fun lastSeen(): Long = prefs.getLong(KEY_LAST_SEEN, 0L)

    fun markSeen() {
        prefs.edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val KEY_LAST_SEEN = "last_seen"
    }
}