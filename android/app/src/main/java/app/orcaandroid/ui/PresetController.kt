package app.orcaandroid.ui

import android.net.Uri
import app.orcaandroid.R
import app.orcaandroid.core.FilamentSlot
import app.orcaandroid.core.OptionDef
import app.orcaandroid.core.PresetType
import app.orcaandroid.core.SettingsGroup
import app.orcaandroid.core.SettingsPage
import app.orcaandroid.core.map
import app.orcaandroid.core.printerKey
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Printer selection, presets and their edits, filament slots and the settings editor. */
class PresetController(private val store: Store, private val device: DeviceController) {
    private val engine = store.engine
    private val settings = store.settings
    private val resources = store.resources

    // --- Printers ------------------------------------------------------------------------------------

    fun openPrinterSetup() = store.update { it.copy(showPrinterSetup = true) }
    fun closePrinterSetup() = store.update { it.copy(showPrinterSetup = false) }

    /** Installs the vendors of the chosen printers (and drops unused ones), then activates a new pick. */
    fun applyPrinterSelection(keys: Set<String>) = store.launch(store.str(R.string.installing_profiles)) {
        val s = store.value
        val added = keys - s.selectedPrinters
        val vendorIds = s.vendors.filter { v -> v.models.any { m -> m.nozzles.any { printerKey(m.name, it) in keys } } }.map { it.id }.toSet()
        resources.setInstalledVendors(vendorIds)
        settings.selectedPrinters = keys
        store.update { it.copy(selectedPrinters = keys, showPrinterSetup = false) }
        reloadPresets(added)
        store.update { it.copy(phase = Phase.READY) }
    }

    fun isFavorite(type: PresetType, key: String) = "${type.id}:$key" in store.value.favorites

    /** Adds or removes an option from the favourites (settings editor star, print panel). */
    fun toggleFavorite(type: PresetType, key: String) {
        val id = "${type.id}:$key"
        val list = store.value.favorites.let { if (id in it) it - id else it + id }
        settings.favoriteOptions = list
        store.update { it.copy(favorites = list) }
    }

    /** Stars all [keys] of a settings group, or unstars them if they all are favourites already. */
    fun toggleFavoriteGroup(type: PresetType, keys: List<String>) {
        val ids = keys.map { "${type.id}:$it" }
        val current = store.value.favorites
        val list = if (ids.all { it in current }) current - ids.toSet() else current + ids.filterNot { it in current }
        settings.favoriteOptions = list
        store.update { it.copy(favorites = list) }
    }

    fun setHiddenFilamentVendors(vendors: Set<String>) {
        settings.hiddenFilamentVendors = vendors
        store.update { it.copy(hiddenFilamentVendors = vendors) }
    }

    /** Reloads all presets and selects a printer: a newly added one, the last used, or any 0.4 mm one. */
    suspend fun reloadPresets(prefer: Set<String>) {
        store.update { it.copy(busy = store.str(R.string.loading_profiles)) }
        val printers = engine.loadPresets()
        store.update { it.copy(printers = printers, busy = null) }
        val visible = store.value.visiblePrinters
        val printer = visible.filter { it.system && it.key in prefer }.minByOrNull { abs(it.nozzle - 0.4) }
            ?: visible.firstOrNull { it.name == settings.lastPrinter }
            ?: visible.firstOrNull { it.system && abs(it.nozzle - 0.4) < 1e-3 }
            ?: visible.firstOrNull()
        printer?.let { selectPrinterNow(it.name) }
    }

    fun selectPrinter(name: String) = store.launch(store.str(R.string.selecting_printer)) { selectPrinterNow(name) }

    /** Activates printer [name] with [print] and [filaments] if given, else the ones last used with it. */
    suspend fun selectPrinterNow(name: String, print: String? = null, filaments: List<FilamentSlot>? = null) {
        val setup = engine.selectPrinter(name)
        settings.lastPrinter = name
        val p = print?.takeIf { n -> setup.prints.any { it.name == n } }
            ?: settings.lastPrint(name)?.takeIf { n -> setup.prints.any { it.name == n } }
            ?: setup.defaultPrint
        val fils = withColors((filaments ?: settings.lastFilaments(name)).filter { f -> setup.filaments.any { it.name == f.preset } }
            .ifEmpty { listOf(FilamentSlot(setup.defaultFilament)) })
        store.update {
            it.copy(printer = name, setup = setup, print = p, filaments = fils, activeFilament = 0, overrides = emptyMap(), results = emptyMap())
        }
        refreshPresetValues(PresetType.entries)
        device.refreshConnection()
        store.applyScene(engine.scene(), dirty = false)
    }

    /** Reads the option values of the selected presets of [types] and pushes the selection to the engine. */
    suspend fun refreshPresetValues(types: Collection<PresetType>) {
        val s = store.value
        val values = s.presetValues.toMutableMap()
        for (type in types) s.presetName(type)?.let { values[type] = engine.presetValues(type, it) }
        store.update { it.copy(presetValues = values) }
        syncSelection()
        refreshOptionStates()
    }

    /** Sends the current preset selection and edits to the engine (needed before slicing etc.). */
    suspend fun syncSelection() {
        val s = store.value
        val print = s.print ?: return
        if (s.filaments.isEmpty()) return
        engine.setSelection(print, s.filaments, s.overrides[PresetType.PRINTER].orEmpty() + s.overrides[PresetType.PRINT].orEmpty())
    }

    private suspend fun refreshOptionStates() {
        runCatching { engine.optionStates() }.onSuccess { st -> store.update { it.copy(optionStates = st) } }
    }

    fun selectPrint(name: String) = store.launch {
        settings.setLastPrint(store.value.printer ?: return@launch, name)
        store.update { it.copy(print = name, overrides = it.overrides - PresetType.PRINT, results = emptyMap()) }
        refreshPresetValues(listOf(PresetType.PRINT))
    }

    // --- Filament slots ------------------------------------------------------------------------------

    fun setFilament(slot: Int, name: String) = updateFilaments { list -> list.mapIndexed { i, f -> if (i == slot) FilamentSlot(name, f.color) else f } }
    fun addFilament() = updateFilaments { list -> list + FilamentSlot(list.last().preset, nextColor(list.size)) }
    fun removeFilament(slot: Int) = updateFilaments { list -> if (list.size <= 1) list else list.filterIndexed { i, _ -> i != slot } }
    fun setFilamentColor(slot: Int, color: String) = updateFilaments { list -> list.mapIndexed { i, f -> if (i == slot) f.copy(color = color) else f } }

    fun setActiveFilament(slot: Int) = store.launch {
        store.update { it.copy(activeFilament = slot.coerceIn(0, it.filaments.size - 1)) }
        refreshPresetValues(listOf(PresetType.FILAMENT))
    }

    private fun updateFilaments(transform: (List<FilamentSlot>) -> List<FilamentSlot>) = store.launch {
        val s = store.value
        val list = transform(s.filaments)
        settings.setLastFilaments(s.printer ?: return@launch, list)
        store.update { it.copy(filaments = list, activeFilament = it.activeFilament.coerceIn(0, list.size - 1), results = emptyMap()) }
        refreshPresetValues(listOf(PresetType.FILAMENT))
    }

    private fun nextColor(i: Int) = SLOT_COLORS[i % SLOT_COLORS.size]

    // --- Settings editor -----------------------------------------------------------------------------

    fun openEditor(target: EditorTarget) = store.launch {
        if (target is EditorTarget.Preset && target.type == PresetType.FILAMENT) refreshPresetValues(listOf(PresetType.FILAMENT))
        store.update { it.copy(editor = target) }
    }

    /** Back from a comparison returns to the preset it was opened from; everything else closes the editor. */
    fun closeEditor() = store.update { s ->
        val e = s.editor
        s.copy(editor = if (e is EditorTarget.Compare) EditorTarget.Preset(e.type) else null)
    }

    suspend fun optionDefs(type: PresetType): List<OptionDef> = engine.optionDefs(type)

    suspend fun presetValuesOf(type: PresetType, name: String): Map<String, String> = engine.presetValues(type, name)

    /** Desktop settings layout (pages and groups) of a preset type. */
    fun settingsLayout(type: PresetType): List<SettingsPage> {
        val json = store.app.assets.open("settings_layout.json").bufferedReader().use { it.readText() }
        return JSONObject(json).getJSONArray(type.id).map { p ->
            val page = p as JSONObject
            SettingsPage(page.getString("page"), page.getJSONArray("groups").map { g ->
                val group = g as JSONObject
                SettingsGroup(group.getString("group"), group.getJSONArray("options").map { it as String })
            })
        }
    }

    /** Sets an option; setting it back to the preset's value drops the edit. */
    fun setOption(type: PresetType, key: String, value: String) = editOverrides(type) { current ->
        val isDefault = store.value.presetValues[type]?.get(key) == value
        if (isDefault) current - key else current + (key to value)
    }

    fun resetOption(type: PresetType, key: String) = editOverrides(type) { it - key }

    fun resetAll(type: PresetType) = editOverrides(type) { emptyMap() }

    /** Applies [edit] to the edits of [type] (filament: of the active slot) and syncs the engine. */
    private fun editOverrides(type: PresetType, edit: (Map<String, String>) -> Map<String, String>) = store.launch {
        store.update { s ->
            if (type == PresetType.FILAMENT) {
                s.copy(filaments = s.filaments.mapIndexed { i, f -> if (i == s.activeFilament) f.copy(overrides = edit(f.overrides)) else f }, results = emptyMap())
            } else {
                s.copy(overrides = s.overrides + (type to edit(s.overrides[type].orEmpty())), results = emptyMap())
            }
        }
        syncSelection()
        refreshOptionStates()
    }

    fun savePresetAs(type: PresetType, name: String) = store.launch(store.str(R.string.saving_preset)) {
        val s = store.value
        val base = s.presetName(type) ?: return@launch
        val newName = name.trim()
        val setup = engine.savePreset(type, base, newName, s.overridesOf(type))
        when (type) {
            PresetType.PRINTER -> {
                val printers = engine.printerList()
                store.update { it.copy(printers = printers, overrides = it.overrides - type) }
                selectPrinterNow(newName, s.print, s.filaments)
            }
            PresetType.PRINT -> {
                s.printer?.let { settings.setLastPrint(it, newName) }
                store.update { it.copy(setup = setup, print = newName, overrides = it.overrides - type) }
            }
            PresetType.FILAMENT -> store.update {
                it.copy(setup = setup, filaments = it.filaments.mapIndexed { i, f -> if (i == it.activeFilament) FilamentSlot(newName, f.color) else f })
            }
        }
        refreshPresetValues(listOf(type))
    }

    fun deleteSelectedPreset(type: PresetType) = store.launch(store.str(R.string.deleting_preset)) {
        val name = store.value.presetName(type) ?: return@launch
        val setup = engine.deletePreset(type, name)
        when (type) {
            PresetType.PRINTER -> {
                val printers = engine.printerList()
                store.update { it.copy(printers = printers) }
                store.value.visiblePrinters.firstOrNull()?.let { selectPrinterNow(it.name) }
            }
            PresetType.PRINT -> store.update { it.copy(setup = setup, print = setup.defaultPrint, overrides = it.overrides - type) }
            PresetType.FILAMENT -> store.update {
                it.copy(setup = setup, filaments = it.filaments.mapIndexed { i, f -> if (i == it.activeFilament) FilamentSlot(setup.defaultFilament, f.color) else f })
            }
        }
        refreshPresetValues(listOf(type))
    }

    fun isUserPreset(type: PresetType): Boolean {
        val s = store.value
        val name = s.presetName(type) ?: return false
        return when (type) {
            PresetType.PRINTER -> s.printers.any { it.name == name && !it.system }
            PresetType.PRINT -> s.setup?.prints?.any { it.name == name && !it.system } == true
            PresetType.FILAMENT -> s.setup?.filaments?.any { it.name == name && !it.system } == true
        }
    }

    fun importPresets(uris: List<Uri>) = store.launch(store.str(R.string.importing_presets)) {
        val files = uris.map { resources.importFile(it, "presets").path }
        val (printers, setup) = engine.importPresets(files)
        store.update { it.copy(printers = printers, setup = setup) }
        store.toast(store.str(R.string.presets_imported, files.size))
    }

    fun exportPreset(type: PresetType, target: Uri) = store.launch {
        val path = engine.presetFile(type, store.value.presetName(type) ?: return@launch)
        withContext(Dispatchers.IO) {
            store.app.contentResolver.openOutputStream(target, "wt")!!.use { out -> File(path).inputStream().use { it.copyTo(out) } }
        }
        store.toast(store.str(R.string.preset_exported))
    }

    // --- Profile updates -----------------------------------------------------------------------------

    fun checkProfileUpdates() = store.launch(store.str(R.string.checking_updates)) {
        val updates = resources.checkProfileUpdates()
        store.update { it.copy(profileUpdates = updates) }
        if (updates.isEmpty()) store.toast(store.str(R.string.profiles_up_to_date))
    }

    /** Installs the found updates in the background; the app stays usable meanwhile. */
    fun installProfileUpdates() {
        if (store.value.profileUpdateProgress != null) return
        store.launch {
            try {
                for (u in store.value.profileUpdates.orEmpty()) {
                    resources.updateVendor(u.vendor) { done, total ->
                        store.update { it.copy(profileUpdateProgress = store.str(R.string.updating_vendor, u.vendor, done, total)) }
                    }
                }
            } finally {
                store.update { it.copy(profileUpdateProgress = null) }
            }
            store.update { it.copy(profileUpdates = null) }
            reloadPresets(emptySet())
            store.toast(store.str(R.string.profiles_updated))
        }
    }

    companion object {
        private val SLOT_COLORS = listOf("#FF7F27", "#2F7FEF", "#2FBF4F", "#EF3F3F", "#FFFFFF", "#202020", "#FFD700", "#8F3FDF")

        /**
         * Gives every slot an explicit colour, so the plate, the G-code, thumbnails and saved
         * projects all use the colour the app shows (instead of the filament preset's own).
         */
        fun withColors(slots: List<FilamentSlot>): List<FilamentSlot> =
            slots.mapIndexed { i, f -> if (f.color.isNullOrBlank()) f.copy(color = SLOT_COLORS[i % SLOT_COLORS.size]) else f }
    }
}
