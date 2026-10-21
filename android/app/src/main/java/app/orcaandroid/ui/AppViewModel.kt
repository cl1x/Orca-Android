package app.orcaandroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.orcaandroid.core.ThemeMode
import app.orcaandroid.core.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point of the UI to the app's state and actions. The native engine keeps the scene and
 * presets; [state] mirrors what the UI needs. Actions are grouped by topic into controllers that
 * share one [Store].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app, viewModelScope)

    val state: StateFlow<UiState> = store.state.asStateFlow()

    val device = DeviceController(store)
    val presets = PresetController(store, device)
    val scene = SceneController(store, presets)
    val slicing = SliceController(store, presets)
    val files = FileController(store, presets, scene, slicing)

    /** Desktop translations of option labels for the device language. */
    var translator: Translator = Translator.NONE
        private set

    init {
        device.files = files
        viewModelScope.launch {
            runCatching { start() }
                .onFailure { e -> store.update { it.copy(phase = Phase.SETUP, error = e.message ?: e.toString()) } }
        }
        viewModelScope.launch { store.container.printerStatus.collect { s -> store.update { it.copy(printerStatus = s) } } }
    }

    /** Unpacks the resources, starts the engine and loads the printers chosen before (or asks for them). */
    private suspend fun start() {
        val resources = store.resources
        translator = withContext(Dispatchers.IO) { Translator.load(store.app) }
        resources.prepare()
        store.engine.init(resources.resourcesDir, resources.dataDir)
        val vendors = withContext(Dispatchers.IO) { resources.availableVendors() }
        val selected = store.settings.selectedPrinters
        store.update { it.copy(vendors = vendors, selectedPrinters = selected) }
        val installed = resources.installedVendorIds()
        if (selected.isEmpty() || installed.none { id -> vendors.any { v -> v.id == id && !v.required } }) {
            store.update { it.copy(phase = Phase.SETUP) }
        } else {
            presets.reloadPresets(emptySet())
            // The workspace is usable at once; the last session's models follow in the background.
            store.update { it.copy(phase = Phase.READY, restoringSession = files.hasSession()) }
            if (store.value.restoringSession) store.launch {
                try {
                    files.restoreSession()
                } catch (e: Exception) {
                    android.util.Log.w("Orca", "session not restored", e)
                } finally {
                    store.update { it.copy(restoringSession = false) }
                }
            }
        }
    }

    /** Waits until startup has finished, including the session (intents that arrive while the app starts). */
    suspend fun awaitReady() = state.first { it.phase == Phase.READY && !it.restoringSession }

    // --- App-wide UI state ---------------------------------------------------------------------------

    fun setScreen(screen: Screen) = store.update { it.copy(screen = screen, tool = if (screen == Screen.PREPARE) it.tool else Tool.None) }

    fun setThemeMode(mode: ThemeMode) {
        store.settings.themeMode = mode
        store.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(on: Boolean) {
        store.settings.dynamicColor = on
        store.update { it.copy(dynamicColor = on) }
    }

    /** Keeps the current state for the next start; runs outside the view model so it survives the activity. */
    fun saveSession() = store.container.appScope.launch {
        runCatching { files.saveSession() }.onFailure { android.util.Log.w("Orca", "session not saved", it) }
    }

    fun dismissError() = store.update { it.copy(error = null) }
    fun dismissMessage() = store.update { it.copy(message = null) }
    fun showError(message: String) = store.update { it.copy(error = message) }
}
