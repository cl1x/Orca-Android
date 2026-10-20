package app.orcaandroid.ui.preview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import app.orcaandroid.render.PlateView
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.UiState
import app.orcaandroid.ui.components.framePlate
import java.io.File
import java.util.Locale

/** Colour schemes of the preview, in PreviewStyle.scheme order. */
internal val SCHEMES = listOf(
    R.string.scheme_type, R.string.scheme_speed, R.string.scheme_height, R.string.scheme_width,
    R.string.scheme_fan, R.string.scheme_temperature, R.string.scheme_volumetric, R.string.scheme_filament,
)

/** Plate / scheme selection on top, the layer slider on the right and the move slider at the bottom. */
@Composable
fun PreviewOverlay(state: UiState, vm: AppViewModel, view: PlateView?, wide: Boolean) {
    val result = state.shownResult
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.external != null) {
                    AssistChip(onClick = vm.slicing::closeExternal, label = { Text(File(state.external.gcodeFile).name, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.close), Modifier.size(16.dp)) })
                } else if (state.scene.plates.size > 1) {
                    state.scene.plates.forEach { p ->
                        FilterChip(
                            selected = p.index == state.previewPlate,
                            onClick = { vm.slicing.setPreviewPlate(p.index); view?.framePlate(p.index) },
                            label = { Text(stringResource(R.string.plate_n, p.index + 1) + if (state.results.containsKey(p.index)) " ✓" else "") },
                        )
                    }
                }
            }
            if (result != null) SchemeButton(state, vm)
        }
        if (result != null && result.layers.isNotEmpty()) {
            EdgeSliders(state, vm)
        }
    }
}

@Composable
private fun SchemeButton(state: UiState, vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = true }, Modifier.size(40.dp)) { Icon(Icons.Default.Palette, stringResource(R.string.color_scheme)) }
                Text(stringResource(SCHEMES[state.preview.scheme]), Modifier.padding(end = 12.dp).clickable { open = true }, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SCHEMES.forEachIndexed { i, label ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; vm.slicing.updatePreview { it.copy(scheme = i) } })
            }
        }
    }
}

/**
 * The layer range as a slim vertical slider on the right edge and the moves of the top layer as a
 * thin slider along the bottom, both over the view (like the desktop), so the plate keeps the
 * full height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.EdgeSliders(state: UiState, vm: AppViewModel) {
    val result = state.shownResult ?: return
    val layers = result.layers
    val p = state.preview
    val last = layers.lastIndex
    val lo = p.layerLow.coerceIn(0, last)
    val hi = p.layerHigh.coerceIn(lo, last)
    val movesInLayer = (layers.getOrNull(hi + 1)?.extrusion ?: result.extrusionCount) - layers[hi].extrusion

    Text(
        stringResource(R.string.layer_of, hi + 1, layers.size) + "  ·  %.2f mm".format(Locale.ROOT, layers[hi].z),
        Modifier.align(Alignment.TopStart).padding(top = 48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    if (last > 0) {
        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 48.dp, bottom = 40.dp).width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { vm.slicing.updatePreview { it.copy(layerHigh = (hi + 1).coerceAtMost(last), moveEnd = null) } }, Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.layer_up))
            }
            RangeSlider(
                value = lo.toFloat()..hi.toFloat(),
                onValueChange = { r -> vm.slicing.updatePreview { it.copy(layerLow = r.start.toInt(), layerHigh = r.endInclusive.toInt(), moveEnd = null) } },
                valueRange = 0f..last.toFloat(),
                modifier = Modifier.weight(1f).vertical(),
                track = { SliderDefaults.Track(it, Modifier.height(6.dp), drawStopIndicator = null, thumbTrackGapSize = 2.dp) },
            )
            IconButton(onClick = { vm.slicing.updatePreview { it.copy(layerHigh = (hi - 1).coerceAtLeast(lo), moveEnd = null) } }, Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.layer_down))
            }
        }
    }
    if (movesInLayer > 1) {
        Slider(
            value = (p.moveEnd ?: movesInLayer).toFloat(),
            onValueChange = { v -> vm.slicing.updatePreview { it.copy(moveEnd = v.toInt().takeIf { m -> m < movesInLayer }) } },
            valueRange = 1f..movesInLayer.toFloat(),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 44.dp).height(32.dp),
            track = { SliderDefaults.Track(it, Modifier.height(4.dp), drawStopIndicator = null, thumbTrackGapSize = 2.dp) },
        )
    }
}

/** Turns a horizontal slider into a vertical one (bottom = start), filling the given height. */
private fun Modifier.vertical() = graphicsLayer { rotationZ = 270f }.layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight,
        minHeight = 0, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) { placeable.place(-(placeable.width - placeable.height) / 2, (placeable.width - placeable.height) / 2) }
}
