package de.tizian.hamsterrad

import android.content.Context

/** Kleinkram, der einen Neustart überleben muss. */
object Prefs {

    private fun prefs(context: Context) =
        context.getSharedPreferences("hamsterrad", Context.MODE_PRIVATE)

    /**
     * Der Schalterzustand der Aufzeichnung. Ältere Installationen kannten nur
     * „pausiert“; wer damals pausiert hatte, startet mit ausgeschalteter Aufzeichnung.
     */
    fun recording(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains("recording")) return p.getBoolean("recording", true)
        return !p.getBoolean("paused", false)
    }

    fun setRecording(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("recording", value).remove("paused").apply()
    }

    fun address(context: Context): String? = prefs(context).getString("address", null)

    fun name(context: Context): String? = prefs(context).getString("name", null)

    fun setDevice(context: Context, address: String, name: String?) {
        prefs(context).edit().putString("address", address).putString("name", name).apply()
    }

    fun askedNotifications(context: Context): Boolean =
        prefs(context).getBoolean("askedNotifications", false)

    fun setAskedNotifications(context: Context) {
        prefs(context).edit().putBoolean("askedNotifications", true).apply()
    }

    fun askedBattery(context: Context): Boolean = prefs(context).getBoolean("askedBattery", false)

    fun setAskedBattery(context: Context) {
        prefs(context).edit().putBoolean("askedBattery", true).apply()
    }
}
