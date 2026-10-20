package app.orcaandroid.ui

import android.app.Application
import androidx.annotation.StringRes
import app.orcaandroid.container
import app.orcaandroid.core.Scene
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The UI state and the helpers every controller needs: the app services, a coroutine launcher
 * that turns failures into the error dialog, and adopting a new scene from the engine.
 */
class Store(val app: Application, val scope: CoroutineScope) {
    val container = app.container
    val engine = container.engine
    val settings = container.settings
    val resources = container.resources

    val state = MutableStateFlow(
        UiState(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            hiddenFilamentVendors = settings.hiddenFilamentVendors,
            favorites = settings.favoriteOptions,
            recents = settings.recents,
        )
    )

    val value: UiState get() = state.value

    fun update(transform: (UiState) -> UiState) = state.update(transform)

    fun str(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    fun toast(message: String) = update { it.copy(message = message) }

    /** Runs [block], showing [busy] (if any) and turning exceptions into an error dialog. */
    fun launch(busy: String? = null, block: suspend () -> Unit): Job = scope.launch {
        if (busy != null) update { it.copy(busy = busy) }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            update { it.copy(error = e.message ?: e.toString()) }
        } finally {
            if (busy != null) update { it.copy(busy = null) }
        }
    }

    /** Adopts a scene from the engine; any slice result is stale afterwards. */
    fun applyScene(scene: Scene, dirty: Boolean = true) = update { s ->
        s.copy(
            scene = scene,
            selection = s.selection?.takeIf { it.obj < scene.objects.size },
            activePlate = s.activePlate.coerceIn(0, (scene.plates.size - 1).coerceAtLeast(0)),
            results = emptyMap(),
            projectDirty = s.projectDirty || dirty,
        )
    }

    /** Runs an engine operation that returns the new scene. */
    fun sceneOp(busy: String? = null, block: suspend () -> Scene) = launch(busy) { applyScene(block()) }
}
