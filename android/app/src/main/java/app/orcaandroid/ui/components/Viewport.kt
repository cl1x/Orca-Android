package app.orcaandroid.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import app.orcaandroid.core.SliceResult
import app.orcaandroid.core.Vec3
import app.orcaandroid.render.FloatData
import app.orcaandroid.render.InteractionMode
import app.orcaandroid.render.PlateListener
import app.orcaandroid.render.PlateView
import app.orcaandroid.render.PreviewData
import app.orcaandroid.render.PreviewRange
import app.orcaandroid.render.PreviewStyle
import app.orcaandroid.render.ViewColors
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.Screen
import app.orcaandroid.ui.Tool
import app.orcaandroid.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Parses "#RRGGBB" into 0..1 RGB. */
fun hexToRgb(hex: String?): FloatArray {
    val h = hex?.removePrefix("#")?.take(6) ?: return floatArrayOf(0.8f, 0.8f, 0.8f)
    val v = h.toLongOrNull(16) ?: return floatArrayOf(0.8f, 0.8f, 0.8f)
    return floatArrayOf(((v shr 16) and 0xff) / 255f, ((v shr 8) and 0xff) / 255f, (v and 0xff) / 255f)
}

private fun Color.rgb() = floatArrayOf(red, green, blue)

/** Visible index ranges of the preview buffers for the chosen layers / moves. */
fun previewRange(result: SliceResult, low: Int, high: Int, moveEnd: Int?, counts: Triple<Int, Int, Int>): PreviewRange {
    val layers = result.layers
    if (layers.isEmpty()) return PreviewRange()
    val lo = low.coerceIn(0, layers.lastIndex)
    val hi = high.coerceIn(lo, layers.lastIndex)
    val next = layers.getOrNull(hi + 1)
    val eEnd = moveEnd?.let { layers[hi].extrusion + it } ?: (next?.extrusion ?: counts.first)
    return PreviewRange(
        layers[lo].extrusion until eEnd,
        layers[lo].travel until (next?.travel ?: counts.second),
        layers[lo].marker until (next?.marker ?: counts.third),
    )
}

/**
 * The shared 3D view of the Prepare and Preview screens. Pushes scene, selection, tool and
 * preview state to the GL thread and turns touches into view-model actions.
 */
@Composable
fun Viewport(state: UiState, vm: AppViewModel, onView: (PlateView) -> Unit, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<PlateView?>(null) }
    var preview by remember { mutableStateOf<PreviewData?>(null) }
    val showPreview = state.screen == Screen.PREVIEW
    val result = state.shownResult

    val scheme = MaterialTheme.colorScheme
    val colors = ViewColors(
        background = scheme.surfaceContainerHighest.rgb(),
        grid = scheme.outline.rgb(),
        activePlate = scheme.primary.rgb(),
        part = floatArrayOf(0.96f, 0.58f, 0.2f),
        selected = scheme.primary.rgb(),
    )

    AndroidView(
        factory = { ctx ->
            PlateView(ctx).also { v ->
                v.listener = object : PlateListener {
                    override fun onTap(x: Float, y: Float) {
                        val (o, d) = v.camera.ray(x, y)
                        vm.scene.onViewTap(o, d)
                    }
                    override fun onMoved(dx: Float, dy: Float) {
                        v.onGl { dragOffset = floatArrayOf(0f, 0f, 0f) }
                        vm.scene.moveSelected(dx, dy)
                    }
                    override fun onPaint(x: Float, y: Float, newStroke: Boolean) {
                        val (o, d) = v.camera.ray(x, y)
                        vm.scene.paintAt(o, d, v.camera.eye(), newStroke)
                    }
                }
                view = v
                onView(v)
            }
        },
        modifier = modifier,
    )

    val v = view ?: return
    val setup = state.setup
    val scene = state.scene

    LaunchedEffect(v, colors) { v.onGl { this.colors = colors } }
    LaunchedEffect(v, setup?.bed, scene.plates.map { it.originX to it.originY }) {
        val bed = setup?.bed ?: return@LaunchedEffect
        val origins = scene.plates.map { it.originX to it.originY }
        v.onGl {
            val first = plateOriginsCount == 0
            setBed(bed, origins)
            plateOriginsCount = origins.size
            if (first) frame(state.activePlate)
        }
    }
    LaunchedEffect(v, scene.meshVersion, scene.meshFile) {
        if (scene.meshFile.isEmpty()) return@LaunchedEffect
        val mesh = withContext(Dispatchers.IO) { FloatData.load(scene.meshFile, 8) }
        v.onGl { setMesh(mesh) }
    }
    LaunchedEffect(v, scene.paintVersion, scene.paintFile) {
        if (scene.paintFile.isEmpty()) return@LaunchedEffect
        val paint = withContext(Dispatchers.IO) { FloatData.load(scene.paintFile, 8) }
        v.onGl { setPaint(paint) }
    }
    LaunchedEffect(v, result?.previewDir, result?.gcodeFile) {
        val data = result?.let { withContext(Dispatchers.IO) { PreviewData.load(it.previewDir) } }
        preview = data
        v.onGl { setPreview(data) }
    }
    LaunchedEffect(v, state.activePlate) { v.onGl { activePlate = state.activePlate } }
    LaunchedEffect(v, state.filaments) {
        val cols = state.filaments.map { hexToRgb(it.color ?: "#FF7F27") }
        v.onGl { filamentColors = cols }
    }
    LaunchedEffect(v, state.selection, scene) {
        val sel = state.selection
        v.onGl { selectedObject = sel?.obj ?: -1 }
        v.selectedBounds = sel?.let { s -> scene.objects.getOrNull(s.obj)?.instances?.map { it.min to (it.min + it.size) } }.orEmpty()
    }
    // In the preview the view is for looking only: a touch on an object must not move it (which
    // would discard the slice result) or change the selection.
    LaunchedEffect(v, showPreview) { v.viewOnly = showPreview }
    LaunchedEffect(v, state.tool) {
        v.mode = when (state.tool) {
            is Tool.Paint -> InteractionMode.PAINT
            Tool.LayOnFace -> InteractionMode.LAY_ON_FACE
            Tool.Measure -> InteractionMode.MEASURE
            else -> InteractionMode.NAVIGATE
        }
        val cut = (state.tool as? Tool.Cut)?.let { t ->
            state.selectedObject?.instances?.getOrNull(state.selection?.instance ?: 0)?.let { i ->
                floatArrayOf(i.min.x - 5, i.min.y - 5, i.min.x + i.size.x + 5, i.min.y + i.size.y + 5, t.z)
            }
        }
        v.onGl { cutPlane = cut }
    }
    LaunchedEffect(v, state.measure) {
        val pts = state.measure.map { floatArrayOf(it.x, it.y, it.z) }
        v.onGl { measurePoints = pts }
    }
    LaunchedEffect(v, showPreview, result, state.preview, preview) {
        val p = state.preview
        val data = preview
        val style = PreviewStyle(
            scheme = p.scheme,
            hiddenRoles = p.hiddenRoles,
            range = result?.ranges?.get(schemeKey(p.scheme)) ?: (0f to 1f),
            showTravels = p.showTravels,
            showRetracts = p.showRetracts,
            showSeams = p.showSeams,
        )
        val range = if (result != null && data != null)
            previewRange(result, p.layerLow, p.layerHigh, p.moveEnd, Triple(data.extrusions.count, data.travels.count, data.markers.count))
        else PreviewRange()
        v.onGl {
            this.showPreview = showPreview && result != null
            previewOrigin = floatArrayOf(result?.originX ?: 0f, result?.originY ?: 0f)
            previewStyle = style
            previewRange = range
        }
    }
}

/** ranges key of a colour scheme index (see PreviewStyle.scheme). */
fun schemeKey(scheme: Int) = when (scheme) {
    1 -> "speed"; 2 -> "height"; 3 -> "width"; 4 -> "fan"; 5 -> "temperature"; 6 -> "volumetric"; else -> ""
}

/** Frames plate [plate] of the given view. */
fun PlateView.framePlate(plate: Int, top: Boolean = false) = onGl { frame(plate, top) }
