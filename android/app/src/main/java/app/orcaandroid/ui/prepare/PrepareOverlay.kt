package app.orcaandroid.ui.prepare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import app.orcaandroid.core.VolumeType
import app.orcaandroid.render.PlateView
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.SliceStatus
import app.orcaandroid.ui.Tool
import app.orcaandroid.ui.UiState
import app.orcaandroid.ui.components.ConfirmDialog
import app.orcaandroid.ui.components.PickerDialog
import app.orcaandroid.ui.components.PickerItem
import app.orcaandroid.ui.components.framePlate

/** Controls over the 3D view on the Prepare screen. */
@Composable
fun PrepareOverlay(state: UiState, vm: AppViewModel, view: PlateView?, wide: Boolean) {
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TopBar(state, vm, view)
            state.calibration?.let { cal ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.calibration_active, CALIBRATION_LABELS[cal.type]?.let { stringResource(it) } ?: cal.name), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.TextButton(onClick = vm.scene::stopCalibration) { Text(stringResource(R.string.end_calibration)) }
                    }
                }
            }
        }
        // A slim tool bar on the left edge of the view; the slice button is in the panel's summary line.
        Surface(Modifier.align(Alignment.CenterStart).padding(vertical = 56.dp), shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) { ToolButtons(state, vm) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 52.dp)) {
            ToolPanel(state, vm)
        }
    }
}

@Composable
private fun TopBar(state: UiState, vm: AppViewModel, view: PlateView?) {
    var plateMenu by remember { mutableStateOf<Int?>(null) }
    var projectMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.scene.plates.forEach { plate ->
                Box {
                    FilterChip(
                        selected = plate.index == state.activePlate,
                        onClick = {
                            if (plate.index == state.activePlate) plateMenu = plate.index
                            else { vm.scene.selectPlate(plate.index); view?.framePlate(plate.index) }
                        },
                        label = { Text(stringResource(R.string.plate_n, plate.index + 1)) },
                        trailingIcon = if (plate.index == state.activePlate) { { Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp)) } } else null,
                    )
                    if (plateMenu == plate.index) PlateMenu(state, vm, plate.index) { plateMenu = null }
                }
            }
        }
        // Undo/redo, plus one menu for the view, plates and the project.
        IconButton(onClick = vm.scene::undo, enabled = state.scene.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
        IconButton(onClick = vm.scene::redo, enabled = state.scene.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo)) }
        Box {
            IconButton(onClick = { projectMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
            ProjectMenu(state, vm, projectMenu, { projectMenu = false }) {
                listOf(R.string.view_iso to 0, R.string.view_top to 1, R.string.view_front to 2).forEach { (label, id) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { projectMenu = false; view?.camera?.preset(id) },
                        leadingIcon = { Icon(Icons.Default.Videocam, null) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.view_fit)) }, onClick = { projectMenu = false; view?.framePlate(state.activePlate) },
                    leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_plate)) }, onClick = { projectMenu = false; vm.scene.addPlate() },
                    leadingIcon = { Icon(Icons.Default.Add, null) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlateMenu(state: UiState, vm: AppViewModel, plate: Int, onDismiss: () -> Unit) {
    var bedPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var gcodes by remember { mutableStateOf(false) }
    // The menu hides while one of its dialogs is open.
    DropdownMenu(expanded = !bedPicker && !confirmDelete && !gcodes, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.slice_plate)) }, onClick = { onDismiss(); vm.slicing.slice(plate) })
        DropdownMenuItem(text = { Text(stringResource(R.string.arrange_plate)) }, onClick = { onDismiss(); vm.scene.arrange(false) })
        if (state.supportsBedTypes) {
            val current = state.scene.plates.getOrNull(plate)?.bedType?.ifEmpty { null } ?: state.setup?.defaultBedType.orEmpty()
            DropdownMenuItem(text = { Text(stringResource(R.string.plate_type) + ": " + vm.translator.tr(current)) }, onClick = { bedPicker = true })
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.layer_gcodes)) }, onClick = { gcodes = true })
        if (state.scene.plates.size > 1)
            DropdownMenuItem(text = { Text(stringResource(R.string.delete_plate)) }, onClick = { confirmDelete = true })
    }
    if (bedPicker) {
        PickerDialog(stringResource(R.string.plate_type),
            state.scene.plates.getOrNull(plate)?.bedType?.ifEmpty { null } ?: state.setup?.defaultBedType,
            state.setup?.bedTypes.orEmpty().map { PickerItem(it, vm.translator.tr(it)) }, onDismiss = { bedPicker = false; onDismiss() }) {
            bedPicker = false; onDismiss(); vm.scene.setPlateBedType(plate, it)
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.delete_plate), stringResource(R.string.delete_plate_text), stringResource(R.string.delete),
        { vm.scene.deletePlate(plate) }) { confirmDelete = false; onDismiss() }
    if (gcodes) LayerGcodeDialog(state, vm, plate) { gcodes = false; onDismiss() }
}

@Composable
private fun ProjectMenu(state: UiState, vm: AppViewModel, open: Boolean, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val openProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm.files::openProject) }
    val saveProject = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/3mf")) { it?.let(vm.files::saveProject) }
    val exportStl = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/stl")) { it?.let { u -> vm.files.exportStl(u, state.activePlate) } }
    var confirmNew by remember { mutableStateOf(false) }
    // Like the desktop: an unsaved project is named after its first object.
    val base = state.projectName ?: state.scene.objects.firstOrNull()?.name?.substringBeforeLast('.') ?: "project"
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        extra()
        DropdownMenuItem(text = { Text(stringResource(R.string.new_project)) }, onClick = { onDismiss(); if (state.projectDirty) confirmNew = true else vm.files.newProject() })
        DropdownMenuItem(text = { Text(stringResource(R.string.open_project)) }, onClick = { onDismiss(); openProject.launch(arrayOf("*/*")) })
        DropdownMenuItem(text = { Text(stringResource(R.string.save_project)) }, onClick = { onDismiss(); saveProject.launch("$base.3mf") },
            enabled = !state.scene.isEmpty)
        DropdownMenuItem(text = { Text(stringResource(R.string.export_stl)) }, onClick = { onDismiss(); exportStl.launch("$base.stl") },
            enabled = !state.scene.isEmpty)
        if (state.recents.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.recent), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            state.recents.take(6).forEach { r ->
                DropdownMenuItem(text = { Text(r.name, maxLines = 1) }, onClick = { onDismiss(); vm.files.openRecent(r) })
            }
        }
    }
    if (confirmNew) ConfirmDialog(stringResource(R.string.new_project), stringResource(R.string.discard_changes), stringResource(R.string.discard),
        vm.files::newProject) { confirmNew = false }
}

/** Context-dependent tool buttons: plate tools always, object tools once something is selected. */
@Composable
private fun ToolButtons(state: UiState, vm: AppViewModel) {
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var paintMenu by remember { mutableStateOf(false) }
    var splitMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val selected = state.selection != null

    Box {
        ToolButton(Icons.Default.Add, stringResource(R.string.add), active = false) { addMenu = true }
        AddMenu(state, vm, addMenu, onDismiss = { addMenu = false }, onDialog = { dialog = it })
    }
    ToolButton(Icons.Default.AutoAwesomeMosaic, stringResource(R.string.arrange), enabled = !state.scene.isEmpty) { vm.scene.arrange(false) }
    ToolButton(Icons.Default._3dRotation, stringResource(R.string.auto_orient), enabled = !state.scene.isEmpty) { vm.scene.autoOrient() }
    if (selected) {
        ToolButton(Icons.Default.VerticalAlignBottom, stringResource(R.string.lay_on_face), active = state.tool == Tool.LayOnFace) {
            vm.scene.setTool(if (state.tool == Tool.LayOnFace) Tool.None else Tool.LayOnFace)
        }
        ToolButton(Icons.Default.ContentCut, stringResource(R.string.cut), active = state.tool is Tool.Cut) {
            val inst = state.selectedObject?.instances?.getOrNull(state.selection!!.instance)
            vm.scene.setTool(if (state.tool is Tool.Cut) Tool.None else Tool.Cut((inst?.let { it.min.z + it.size.z / 2 }) ?: 5f))
        }
        Box {
            ToolButton(Icons.Default.Brush, stringResource(R.string.paint), active = state.tool is Tool.Paint) { paintMenu = true }
            DropdownMenu(expanded = paintMenu, onDismissRequest = { paintMenu = false }) {
                listOf(
                    Triple(R.string.paint_supports, "support", 1),
                    Triple(R.string.paint_seam, "seam", 1),
                    Triple(R.string.paint_fuzzy, "fuzzy", 1),
                    Triple(R.string.paint_color, "color", 2),
                ).forEach { (label, kind, state0) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { paintMenu = false; vm.scene.setTool(Tool.Paint(kind, state0, 3f)) })
                }
            }
        }
        ToolButton(Icons.Default.Layers, stringResource(R.string.variable_layer_height), active = state.tool == Tool.LayerHeight) {
            vm.scene.setTool(if (state.tool == Tool.LayerHeight) Tool.None else Tool.LayerHeight)
        }
        ToolButton(Icons.Default.ContentCopy, stringResource(R.string.duplicate)) { vm.scene.duplicate(1) }
        ToolButton(Icons.Default.Delete, stringResource(R.string.delete)) { vm.scene.deleteSelected() }
        Box {
            ToolButton(Icons.Default.CallSplit, stringResource(R.string.split)) { splitMenu = true }
            DropdownMenu(expanded = splitMenu, onDismissRequest = { splitMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.split_objects)) }, onClick = { splitMenu = false; vm.scene.split(false) })
                DropdownMenuItem(text = { Text(stringResource(R.string.split_parts)) }, onClick = { splitMenu = false; vm.scene.split(true) })
            }
        }
        Box {
            ToolButton(Icons.Default.MoreVert, stringResource(R.string.more)) { moreMenu = true }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.add_modifier)) }, onClick = { moreMenu = false; vm.scene.addVolume(VolumeType.MODIFIER, "box") })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_support_blocker)) }, onClick = { moreMenu = false; vm.scene.addVolume(VolumeType.SUPPORT_BLOCKER, "box") })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_support_enforcer)) }, onClick = { moreMenu = false; vm.scene.addVolume(VolumeType.SUPPORT_ENFORCER, "box") })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_negative_volume)) }, onClick = { moreMenu = false; vm.scene.addVolume(VolumeType.NEGATIVE, "box") })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.simplify)) }, onClick = { moreMenu = false; dialog = "simplify" })
                DropdownMenuItem(text = { Text(stringResource(R.string.repair)) }, onClick = { moreMenu = false; vm.scene.repair() })
                DropdownMenuItem(text = { Text(stringResource(R.string.measure)) }, onClick = { moreMenu = false; vm.scene.setTool(Tool.Measure) },
                    leadingIcon = { Icon(Icons.Default.Straighten, null) })
            }
        }
    } else if (!state.scene.isEmpty) {
        ToolButton(Icons.Default.Straighten, stringResource(R.string.measure), active = state.tool == Tool.Measure) {
            vm.scene.setTool(if (state.tool == Tool.Measure) Tool.None else Tool.Measure)
        }
    }
    AddDialogs(state, vm, dialog) { dialog = null }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors()
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (active) androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        else colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier = Modifier.size(44.dp),
    ) { Icon(icon, label, Modifier.size(22.dp)) }
}

/**
 * The summary line heading the panel (tablet side panel, phone sheet) on the Prepare screen:
 * printer, filaments and process, and the slice button (or the progress of a running slice).
 */
@Composable
fun PrepareSummary(state: UiState, vm: AppViewModel) {
    val running = state.slice as? SliceStatus.Running
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (running != null) {
                Text("${running.text} · ${running.percent} %", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { running.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            } else {
                val filaments = state.filaments.map { it.preset.substringBefore(" @") }
                val first = filaments.firstOrNull().orEmpty() + if (filaments.size > 1) " +${filaments.size - 1}" else ""
                Text(listOf(state.printer.orEmpty(), first).filter { it.isNotEmpty() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.print.orEmpty().substringBefore(" @"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (running != null) {
            OutlinedButton(onClick = vm.slicing::cancelSlice) { Text(stringResource(R.string.cancel)) }
        } else {
            var menu by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { if (!state.scene.isEmpty) vm.slicing.slice() }, enabled = !state.scene.isEmpty) {
                    Icon(Icons.Default.Layers, null, Modifier.size(18.dp))
                    Text(" " + stringResource(if (state.scene.plates.size > 1) R.string.slice_plate_n else R.string.slice, state.activePlate + 1))
                }
                if (state.scene.plates.size > 1) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)) { Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.slice_all)) }, onClick = { menu = false; vm.slicing.sliceAll() })
                    }
                }
            }
        }
    }
}
