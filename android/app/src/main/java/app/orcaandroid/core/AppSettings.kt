package app.orcaandroid.core

import android.content.Context
import app.orcaandroid.net.PrinterConnection
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A recently opened or saved file. */
data class RecentFile(val uri: String, val name: String, val isProject: Boolean, val time: Long)

/** Persists app-wide settings, remembered selections and per-printer connections. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString("theme", value.name).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", false)
        set(value) = prefs.edit().putBoolean("dynamic_color", value).apply()

    /** Printers chosen in the setup, as printerKey(model, nozzle). */
    var selectedPrinters: Set<String>
        get() = prefs.getStringSet("selected_printers", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("selected_printers", value).apply()

    /** Filament vendors/types hidden from the filament lists (like the desktop's filament selection). */
    var hiddenFilamentVendors: Set<String>
        get() = prefs.getStringSet("hidden_filament_vendors", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("hidden_filament_vendors", value).apply()

    /** Favourite options as "type:key" (e.g. "print:layer_height"), in the order they were added. */
    var favoriteOptions: List<String>
        get() = prefs.getString("favorite_options", null)?.split('\n')?.filter { it.isNotBlank() } ?: DEFAULT_FAVORITES
        set(value) = prefs.edit().putString("favorite_options", value.joinToString("\n")).apply()

    var lastPrinter: String?
        get() = prefs.getString("printer", null)
        set(value) = prefs.edit().putString("printer", value).apply()

    fun lastPrint(printer: String): String? = prefs.getString("print:$printer", null)
    fun setLastPrint(printer: String, print: String) = prefs.edit().putString("print:$printer", print).apply()

    fun lastFilaments(printer: String): List<FilamentSlot> = prefs.getString("filaments:$printer", null)?.let { json ->
        runCatching {
            JSONArray(json).map {
                val o = it as JSONObject
                FilamentSlot(o.getString("preset"), o.optString("color").ifEmpty { null })
            }
        }.getOrNull()
    }.orEmpty()

    fun setLastFilaments(printer: String, slots: List<FilamentSlot>) = prefs.edit().putString(
        "filaments:$printer",
        JSONArray(slots.map { JSONObject().put("preset", it.preset).put("color", it.color ?: "") }).toString(),
    ).apply()

    fun connection(printer: String): PrinterConnection? =
        prefs.getString("conn:$printer", null)?.let { runCatching { PrinterConnection.fromJson(it) }.getOrNull() }

    fun setConnection(printer: String, connection: PrinterConnection?) {
        prefs.edit().apply {
            if (connection == null) remove("conn:$printer") else putString("conn:$printer", connection.toJson())
        }.apply()
    }

    var recents: List<RecentFile>
        get() = runCatching {
            JSONArray(prefs.getString("recents", "[]")).map {
                val o = it as JSONObject
                RecentFile(o.getString("uri"), o.getString("name"), o.optBoolean("project"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString("recents", JSONArray(value.map {
            JSONObject().put("uri", it.uri).put("name", it.name).put("project", it.isProject).put("time", it.time)
        }).toString()).apply()

    fun addRecent(file: RecentFile) {
        recents = (listOf(file) + recents.filter { it.uri != file.uri }).take(12)
    }

    companion object {
        /** The options the print panel showed before favourites existed. */
        val DEFAULT_FAVORITES = listOf("print:layer_height", "print:sparse_infill_density", "print:wall_loops", "print:enable_support", "print:brim_type")
    }
}
