package app.orcaandroid.ui

import android.content.Intent
import android.net.Uri
import app.orcaandroid.R
import app.orcaandroid.core.FilamentSlot
import app.orcaandroid.core.PresetType
import app.orcaandroid.core.ProjectInfo
import app.orcaandroid.core.RecentFile
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opening models, projects and G-code, saving and exporting, and the recent files. */
class FileController(
    private val store: Store,
    private val presets: PresetController,
    private val scene: SceneController,
    private val slicing: SliceController,
) {
    private val engine = store.engine
    private val settings = store.settings
    private val resources = store.resources
    private val outDir get() = File(store.app.cacheDir, "out").apply { mkdirs() }

    /** Opens models (appended to the plate), a project (replacing the scene) or a G-code file (preview). */
    fun openModels(uris: List<Uri>, append: Boolean = true) = store.launch(store.str(R.string.loading_model)) {
        if (uris.isEmpty()) return@launch
        val files = uris.map { resources.importFile(it) }
        files.firstOrNull { it.name.endsWith(".gcode", true) || it.name.endsWith(".gco", true) }?.let {
            slicing.viewGcodeFile(it)
            return@launch
        }
        val project = files.singleOrNull()?.takeIf { it.name.endsWith(".3mf", true) && isProject(it) }
        if (project != null && (!append || store.value.scene.isEmpty)) {
            openProjectFile(project, uris.first())
            return@launch
        }
        val s = engine.loadModels(files.map { it.path }, append = append || !store.value.scene.isEmpty, plate = store.value.activePlate)
        store.applyScene(s)
        uris.forEachIndexed { i, u -> rememberRecent(u, files[i].name, false) }
        store.update { it.copy(selection = Selection(s.objects.lastIndex), screen = Screen.PREPARE) }
    }

    /** 3MF files with a desktop project config are opened as projects (with plates and settings). */
    private fun isProject(file: File): Boolean = runCatching {
        ZipFile(file).use { z -> z.getEntry("Metadata/project_settings.config") != null }
    }.getOrDefault(false)

    fun openProject(uri: Uri) = store.launch(store.str(R.string.opening_project)) { openProjectFile(resources.importFile(uri), uri) }

    /** The loaded project's presets and setting changes become the current ones. */
    fun useProjectSettings() = store.launch(store.str(R.string.opening_project)) {
        val prompt = store.value.projectSettingsPrompt ?: return@launch
        store.update { it.copy(projectSettingsPrompt = null) }
        applyProjectSettings(prompt.info)
    }

    /** Keeps the current presets; only the project's objects and plates were loaded. */
    fun keepCurrentSettings() = store.update { it.copy(projectSettingsPrompt = null) }

    /**
     * Loads a project's objects and plates. If its printer, presets or setting changes differ from
     * the current ones, the user is asked which to use ([ask]); otherwise (or for the restored
     * session) the project's settings are applied directly.
     */
    private suspend fun openProjectFile(file: File, uri: Uri?, ask: Boolean = true) {
        val (loaded, info) = engine.loadProject(file.path)
        store.applyScene(loaded, dirty = false)
        uri?.let { rememberRecent(it, file.name, true) }
        store.update { it.copy(projectName = file.name.removeSuffix(".3mf"), projectDirty = false, selection = null, screen = Screen.PREPARE) }
        val prompt = projectPrompt(info) ?: return
        if (ask) store.update { it.copy(projectSettingsPrompt = prompt) } else applyProjectSettings(info)
    }

    /** The differences between the project's settings and the current ones; null if there are none. */
    private fun projectPrompt(info: ProjectInfo): ProjectSettingsPrompt? {
        val s = store.value
        val printer = s.printers.firstOrNull { it.name == info.printer }?.name
        val printerChange = if (printer != null && printer != s.printer) s.printer.orEmpty() to printer else null
        val printChange = if (info.print.isNotBlank() && info.print != s.print) s.print.orEmpty() to info.print else null
        val current = s.filaments.map { it.preset }
        val filamentChange = if (info.filaments.isNotEmpty() && info.filaments != current)
            current.joinToString(", ") to info.filaments.joinToString(", ") else null
        val printEdits = info.printOverrides.filter { (k, v) -> s.overrides[PresetType.PRINT]?.get(k) != v }.size
        val filamentEdits = info.filamentOverrides.withIndex().sumOf { (i, o) -> o.count { (k, v) -> s.filaments.getOrNull(i)?.overrides?.get(k) != v } }
        val changed = printEdits + filamentEdits
        if (printerChange == null && printChange == null && filamentChange == null && changed == 0) return null
        return ProjectSettingsPrompt(info, printerChange, printChange, filamentChange, changed)
    }

    /** Uses the project's presets where they exist here, with its setting changes on top. */
    private suspend fun applyProjectSettings(info: ProjectInfo) {
        val s = store.value
        val printer = s.printers.firstOrNull { it.name == info.printer }?.name ?: s.printer
        val filaments = PresetController.withColors(info.filaments.mapIndexed { i, name ->
            FilamentSlot(name, info.filamentColors.getOrNull(i)?.ifBlank { null }, info.filamentOverrides.getOrNull(i).orEmpty())
        })
        if (printer != null) presets.selectPrinterNow(printer, info.print, filaments.ifEmpty { null })
        store.update { st ->
            val slots = if (filaments.isEmpty()) st.filaments
            else filaments.map { f -> if (st.setup?.filaments?.any { it.name == f.preset } == true) f else f.copy(preset = st.filaments.first().preset) }
            st.copy(filaments = slots, overrides = if (info.printOverrides.isEmpty()) st.overrides else st.overrides + (PresetType.PRINT to info.printOverrides))
        }
        presets.refreshPresetValues(listOf(PresetType.FILAMENT, PresetType.PRINT))
        store.update { it.copy(projectDirty = false) }
    }

    // --- Session: the app comes back as it was left, even after Android ended the process ---------------

    private val sessionDir get() = File(store.app.filesDir, "session").apply { mkdirs() }
    private val sessionProject get() = File(sessionDir, "session.3mf")
    private val sessionInfo get() = File(sessionDir, "session.json")

    /** Saves the scene, presets and setting changes as a project (called when the app goes to the background). */
    suspend fun saveSession() {
        val s = store.value
        // While the session is still loading, the saved one is the latest state.
        if (s.phase != Phase.READY || s.restoringSession) return
        // A calibration test cannot be resumed from a project; an empty plate needs no session.
        if (s.scene.isEmpty || s.calibration != null) {
            sessionProject.delete(); sessionInfo.delete()
            return
        }
        presets.syncSelection()
        val tmp = File(sessionDir, "session.tmp.3mf")
        engine.saveProject(tmp.path)
        withContext(Dispatchers.IO) {
            tmp.renameTo(sessionProject)
            sessionInfo.writeText(JSONObject().apply {
                put("projectName", s.projectName ?: "")
                put("projectDirty", s.projectDirty)
                put("activePlate", s.activePlate)
            }.toString())
        }
    }

    fun hasSession() = sessionProject.exists() && store.value.scene.isEmpty

    /** Restores the last session if the engine has no scene (a fresh start of the process). */
    suspend fun restoreSession() {
        if (!store.value.scene.isEmpty || !sessionProject.exists()) return
        val info = runCatching { JSONObject(sessionInfo.readText()) }.getOrNull()
        openProjectFile(sessionProject, null, ask = false)
        store.update {
            it.copy(
                projectName = info?.optString("projectName")?.ifBlank { null },
                projectDirty = info?.optBoolean("projectDirty") ?: true,
                activePlate = (info?.optInt("activePlate") ?: 0).coerceIn(0, (it.scene.plates.size - 1).coerceAtLeast(0)),
            )
        }
    }

    fun newProject() = store.launch {
        scene.deleteAll().join()
        if (store.value.calibration != null) store.applyScene(engine.calibStop())
        store.update { it.copy(projectName = null, projectDirty = false, calibration = null, results = emptyMap(), external = null) }
    }

    fun saveProject(target: Uri) = store.launch(store.str(R.string.saving_project)) {
        presets.syncSelection()
        val tmp = File(outDir, "project.3mf")
        engine.saveProject(tmp.path)
        copyTo(tmp, target)
        val name = resources.displayName(target).removeSuffix(".3mf")
        rememberRecent(target, "$name.3mf", true)
        store.update { it.copy(projectName = name, projectDirty = false) }
        store.toast(store.str(R.string.project_saved))
    }

    fun exportStl(target: Uri, plate: Int) = store.launch(store.str(R.string.saving)) {
        val tmp = File(outDir, "export.stl")
        engine.exportStl(tmp.path, plate)
        copyTo(tmp, target)
        store.toast(store.str(R.string.saved))
    }

    /** File name for the G-code of [plate]: project or first object name, with the plate if there are several. */
    fun gcodeFileName(plate: Int = store.value.previewPlate): String {
        val s = store.value
        val base = s.projectName ?: s.scene.objectsOn(plate).firstOrNull()?.name?.substringBeforeLast('.') ?: "plate"
        return if (s.scene.plates.size > 1) "${base}_plate${plate + 1}.gcode" else "$base.gcode"
    }

    fun saveGcode(target: Uri) = store.launch(store.str(R.string.saving)) {
        val r = store.value.shownResult ?: return@launch
        copyTo(File(r.gcodeFile), target)
        store.toast(store.str(R.string.saved))
    }

    /** Saves the preview plate as a sliced-plate archive (.gcode.3mf, as Bambu printers and the desktop use). */
    fun saveGcode3mf(target: Uri) = store.launch(store.str(R.string.saving)) {
        val archive = exportGcode3mf() ?: return@launch
        copyTo(archive, target)
        store.toast(store.str(R.string.saved))
    }

    /** Writes the shown slice result as .gcode.3mf into the cache; null without a result. */
    suspend fun exportGcode3mf(): File? {
        val s = store.value
        val r = s.shownResult ?: return null
        return File(outDir, "export.gcode.3mf").also { engine.exportGcode3mf(s.previewPlate, r.gcodeFile, it.path) }
    }

    fun openRecent(recent: RecentFile) {
        val uri = Uri.parse(recent.uri)
        if (recent.isProject) openProject(uri) else openModels(listOf(uri))
    }

    private fun rememberRecent(uri: Uri, name: String, project: Boolean) {
        // Keep access to documents across restarts where the provider allows it.
        runCatching { store.app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        settings.addRecent(RecentFile(uri.toString(), name, project, System.currentTimeMillis()))
        store.update { it.copy(recents = settings.recents) }
    }

    private suspend fun copyTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        store.app.contentResolver.openOutputStream(target, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
}
