package app.orcaandroid.ui.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.orcaandroid.R
import app.orcaandroid.core.SliceResult
import app.orcaandroid.render.PreviewData
import app.orcaandroid.render.Shaders
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.Screen
import app.orcaandroid.ui.UiState
import app.orcaandroid.ui.UploadState
import app.orcaandroid.ui.components.LabeledValue
import app.orcaandroid.ui.components.SectionTitle
import app.orcaandroid.ui.components.formatDuration
import app.orcaandroid.ui.components.previewRange
import app.orcaandroid.ui.components.schemeKey
import app.orcaandroid.ui.prepare.ColorDot
import app.orcaandroid.ui.prepare.LayerGcodeDialog
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extrusion role names as the desktop shows them, by ExtrusionRole value. */
private val ROLE_NAMES = listOf(
    "Undefined", "Inner wall", "Outer wall", "Overhang wall", "Sparse infill", "Internal solid infill", "Top surface",
    "Bottom surface", "Ironing", "Bridge", "Internal Bridge", "Gap infill", "Skirt", "Brim", "Support", "Support interface",
    "Support transition", "Prime tower", "Custom", "Multiple",
)

private fun roleColor(role: Int): Color {
    val c = Shaders.ROLE_COLORS
    val i = role.coerceIn(0, c.size / 3 - 1) * 3
    return Color(c[i], c[i + 1], c[i + 2])
}

/** Summary, legend and actions of the sliced plate. */
@Composable
fun PreviewPanel(state: UiState, vm: AppViewModel, wide: Boolean) {
    val result = state.shownResult
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (result == null) {
            Text(stringResource(if (state.scene.isEmpty) R.string.no_objects_hint else R.string.not_sliced), style = MaterialTheme.typography.bodyMedium)
            if (!state.scene.isEmpty) Button(onClick = { vm.slicing.slice(state.previewPlate) }, enabled = !state.isSlicing) {
                Text(stringResource(R.string.slice_plate_n, state.previewPlate + 1))
            }
            OutlinedButton(onClick = { vm.setScreen(Screen.PREPARE) }) { Text(stringResource(R.string.tab_prepare)) }
            return@Column
        }
        Summary(state, vm, result)
        Actions(state, vm, result)
        if (state.preview.showGcode) GcodeView(state, result)
        Legend(state, vm, result)
        if (!result.external) LayerTools(state, vm, result)
    }
}

@Composable
private fun Summary(state: UiState, vm: AppViewModel, r: SliceResult) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDuration(r.printTimeSeconds), style = MaterialTheme.typography.headlineSmall)
            LabeledValue(stringResource(R.string.filament), "%.2f m · %.1f g".format(Locale.ROOT, r.filamentMm / 1000, r.filamentGrams))
            if (r.cost > 0) LabeledValue(stringResource(R.string.cost), "%.2f".format(Locale.ROOT, r.cost))
            LabeledValue(stringResource(R.string.layers), r.layers.size.toString())
            if (r.filamentUse.size > 1) {
                r.filamentUse.forEachIndexed { i, (m, g) ->
                    if (m > 0f) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorDot(state.filaments.getOrNull(i)?.color)
                        LabeledValue("${i + 1}", "%.2f m · %.1f g".format(Locale.ROOT, m, g))
                    }
                }
            }
            r.warnings.forEach { Text(vm.translator.tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Actions(state: UiState, vm: AppViewModel, r: SliceResult) {
    val context = LocalContext.current
    val saveGcode = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x.gcode")) { it?.let(vm.files::saveGcode) }
    val save3mf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/3mf")) { it?.let(vm.files::saveGcode3mf) }
    var menu by remember { mutableStateOf(false) }
    // Print and send are in the summary line (PreviewSummary); here the file actions.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { saveGcode.launch(vm.files.gcodeFileName()) }) { Icon(Icons.Default.Save, null); Text(" G-code") }
        IconButton(onClick = {
            val file = File(r.gcodeFile)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TITLE, vm.files.gcodeFileName()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null))
        }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
        IconButton(onClick = { vm.slicing.updatePreview { it.copy(showGcode = !it.showGcode) } }) {
            Icon(Icons.Default.Code, stringResource(R.string.show_gcode), tint = if (state.preview.showGcode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!r.external) DropdownMenuItem(text = { Text(stringResource(R.string.save_gcode_3mf)) },
                    onClick = { menu = false; save3mf.launch(vm.files.gcodeFileName().removeSuffix(".gcode") + ".gcode.3mf") })
                if (!r.external && state.scene.plates.size > 1)
                    DropdownMenuItem(text = { Text(stringResource(R.string.slice_all)) }, onClick = { menu = false; vm.slicing.sliceAll() })
                if (r.external) DropdownMenuItem(text = { Text(stringResource(R.string.close)) }, onClick = { menu = false; vm.slicing.closeExternal() })
            }
        }
    }
}

@Composable
private fun Legend(state: UiState, vm: AppViewModel, r: SliceResult) {
    val p = state.preview
    SectionTitle(stringResource(SCHEMES[p.scheme]))
    when (p.scheme) {
        0 -> {
            val total = r.roles.sumOf { it.time.toDouble() }.coerceAtLeast(1.0)
            r.roles.sortedBy { it.role }.forEach { role ->
                val hidden = p.hiddenRoles and (1 shl role.role) != 0
                Row(Modifier.fillMaxWidth().clickable { vm.slicing.updatePreview { it.copy(hiddenRoles = it.hiddenRoles xor (1 shl role.role)) } }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).background(roleColor(role.role), RoundedCornerShape(3.dp)))
                    Text(vm.translator.tr(ROLE_NAMES.getOrElse(role.role) { "?" }), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = if (hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    Text("${formatDuration(role.time.toDouble())} · ${(role.time / total * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(16.dp))
                }
            }
        }
        7 -> state.filaments.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(f.color); Text("${i + 1}: ${f.preset}", style = MaterialTheme.typography.bodySmall)
            }
        }
        else -> {
            val (lo, hi) = r.ranges[schemeKey(p.scheme)] ?: (0f to 0f)
            Box(Modifier.fillMaxWidth().height(12.dp).background(
                Brush.horizontalGradient(listOf(Color(0xFF0B2C7A), Color(0xFF1C9E9E), Color(0xFF4FC34F), Color(0xFFF2E81A), Color(0xFFE33E1F))),
                RoundedCornerShape(6.dp)))
            Row { Text("%.2f".format(Locale.ROOT, lo), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("%.2f".format(Locale.ROOT, hi), style = MaterialTheme.typography.labelSmall) }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(p.showTravels, { vm.slicing.updatePreview { it.copy(showTravels = !it.showTravels) } }, label = { Text(stringResource(R.string.travels)) })
        FilterChip(p.showRetracts, { vm.slicing.updatePreview { it.copy(showRetracts = !it.showRetracts) } }, label = { Text(stringResource(R.string.retractions)) })
        FilterChip(p.showSeams, { vm.slicing.updatePreview { it.copy(showSeams = !it.showSeams) } }, label = { Text(stringResource(R.string.seams)) })
    }
    if (r.travelTime > 0) LabeledValue(stringResource(R.string.travels), formatDuration(r.travelTime.toDouble()))
}

/** Pause / colour change / custom G-code at the top shown layer (desktop: the "+" on the layer slider). */
@Composable
private fun LayerTools(state: UiState, vm: AppViewModel, r: SliceResult) {
    var dialog by remember { mutableStateOf(false) }
    val layer = r.layers.getOrNull(state.preview.layerHigh) ?: return
    HorizontalDivider()
    val count = state.scene.plates.getOrNull(state.previewPlate)?.layerGcodes?.size ?: 0
    TextButton(onClick = { dialog = true }) {
        Text(stringResource(R.string.add_gcode_at_layer, layer.z) + if (count > 0) " ($count)" else "")
    }
    if (dialog) LayerGcodeDialog(state, vm, state.previewPlate, layer.z) { dialog = false }
}

/**
 * The G-code around the last shown move. Line offsets are indexed once per file so that even
 * large files scroll instantly.
 */
@Composable
private fun GcodeView(state: UiState, r: SliceResult) {
    var index by remember { mutableStateOf<LongArray?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var current by remember { mutableStateOf(-1) }
    var data by remember { mutableStateOf<PreviewData?>(null) }
    LaunchedEffect(r.gcodeFile) {
        index = withContext(Dispatchers.IO) { indexLines(r.gcodeFile) }
        data = withContext(Dispatchers.IO) { PreviewData.load(r.previewDir) }
    }
    val p = state.preview
    LaunchedEffect(index, data, p.layerLow, p.layerHigh, p.moveEnd) {
        val idx = index ?: return@LaunchedEffect
        val d = data ?: return@LaunchedEffect
        val range = previewRange(r, p.layerLow, p.layerHigh, p.moveEnd, Triple(d.extrusions.count, d.travels.count, d.markers.count))
        val move = (range.extrusions.last).coerceAtLeast(0)
        val line = d.gcodeLine(move).coerceAtLeast(0)
        current = line
        lines = withContext(Dispatchers.IO) { readLines(r.gcodeFile, idx, (line - 12).coerceAtLeast(0), 25) }
    }
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
            val first = (current - 12).coerceAtLeast(0)
            lines.forEachIndexed { i, text ->
                val n = first + i
                Text("%6d  %s".format(Locale.ROOT, n + 1, text), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall,
                    color = if (n == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

private fun indexLines(path: String): LongArray {
    val offsets = ArrayList<Long>(1 shl 16)
    offsets.add(0L)
    File(path).inputStream().buffered(1 shl 16).use { input ->
        var pos = 0L
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            for (i in 0 until n) if (buf[i] == '\n'.code.toByte()) offsets.add(pos + i + 1)
            pos += n
        }
    }
    return offsets.toLongArray()
}

private fun readLines(path: String, index: LongArray, first: Int, count: Int): List<String> {
    if (first >= index.size) return emptyList()
    val end = minOf(index.size - 1, first + count)
    RandomAccessFile(path, "r").use { f ->
        val start = index[first]
        val stop = if (end < index.size) index[end] else f.length()
        val bytes = ByteArray((stop - start).toInt().coerceAtLeast(0))
        f.seek(start)
        f.readFully(bytes)
        return String(bytes).split('\n').take(count)
    }
}

/** The summary line heading the panel in the preview: time and filament, and print / send. */
@Composable
fun PreviewSummary(state: UiState, vm: AppViewModel) {
    val r = state.shownResult
    val connection = state.connection
    val upload = state.upload
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (r == null) {
                Text(stringResource(if (state.scene.isEmpty) R.string.no_objects_hint else R.string.not_sliced),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            } else {
                Text(formatDuration(r.printTimeSeconds) + "  ·  %.1f g".format(Locale.ROOT, r.filamentGrams),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (upload is UploadState.Running) LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                else Text((upload as? UploadState.Done)?.message ?: "%.2f m ".format(Locale.ROOT, r.filamentMm / 1000) + stringResource(R.string.filament),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        when {
            r == null -> if (!state.scene.isEmpty) Button(onClick = { vm.slicing.slice(state.previewPlate) }, enabled = !state.isSlicing) {
                Text(stringResource(R.string.slice))
            }
            connection?.isConfigured == true && connection.type.canUpload -> {
                OutlinedIconButton(onClick = { vm.device.upload(false) }, enabled = upload !is UploadState.Running) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                }
                Button(onClick = { vm.device.upload(true) }, enabled = upload !is UploadState.Running) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.print))
                }
            }
            else -> OutlinedButton(onClick = { vm.setScreen(Screen.DEVICE) }) { Text(stringResource(R.string.connect)) }
        }
    }
}
