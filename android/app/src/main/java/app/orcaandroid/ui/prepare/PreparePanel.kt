package app.orcaandroid.ui.prepare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import app.orcaandroid.core.LayerRange
import app.orcaandroid.ui.settings.OptionRow
import app.orcaandroid.ui.settings.favoriteGroups
import app.orcaandroid.ui.settings.buildPages
import app.orcaandroid.ui.settings.EnumEditor
import app.orcaandroid.core.OptionDef
import app.orcaandroid.core.overrides
import app.orcaandroid.core.PresetType
import app.orcaandroid.core.Vec3
import app.orcaandroid.core.VolumeType
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.EditorTarget
import app.orcaandroid.ui.Selection
import app.orcaandroid.ui.UiState
import app.orcaandroid.ui.components.NumberField
import app.orcaandroid.ui.components.PickerField
import app.orcaandroid.ui.components.PickerItem
import app.orcaandroid.ui.components.SectionTitle
import app.orcaandroid.ui.components.Vec3Fields

/** Side panel (tablet) / bottom sheet (phone) of the Prepare screen. */
@Composable
fun PreparePanel(state: UiState, vm: AppViewModel, wide: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Selecting an object in the view jumps to the object tab.
    val selKey = state.selection?.obj
    androidx.compose.runtime.LaunchedEffect(selKey) { if (selKey != null) tab = 1 }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tab_print_settings)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_objects) + if (state.scene.objects.isNotEmpty()) " (${state.scene.objects.size})" else "") })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) PresetsTab(state, vm) else ObjectsTab(state, vm)
        }
    }
}

// --- Presets -------------------------------------------------------------------------------------

@Composable
private fun PresetsTab(state: UiState, vm: AppViewModel) {
    val setup = state.setup
    SectionTitle(stringResource(R.string.printer))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PickerField(stringResource(R.string.printer), state.printer,
            state.visiblePrinters.map { PickerItem(it.name, it.name, if (it.system) it.vendor else vm.translator.tr("User presets")) },
            vm.presets::selectPrinter, Modifier.weight(1f), modified = state.overrides[PresetType.PRINTER].orEmpty().isNotEmpty())
        IconButton(onClick = { vm.presets.openEditor(EditorTarget.Preset(PresetType.PRINTER)) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
    }
    TextButton(onClick = vm.presets::openPrinterSetup) { Text(stringResource(R.string.manage_printers)) }
    if (state.supportsBedTypes) {
        val plate = state.scene.plates.getOrNull(state.activePlate)
        PickerField(stringResource(R.string.plate_type), plate?.bedType?.ifEmpty { null } ?: setup?.defaultBedType,
            setup?.bedTypes.orEmpty().map { PickerItem(it, vm.translator.tr(it)) }, { vm.scene.setPlateBedType(state.activePlate, it) })
    }

    SectionTitle(stringResource(R.string.filaments))
    state.filaments.forEachIndexed { i, slot ->
        var colorDialog by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { colorDialog = true }, modifier = Modifier.size(36.dp)) { ColorDot(slot.color, 22) }
            val filaments = setup?.filaments.orEmpty().filter { it.system.not() || it.vendor !in state.hiddenFilamentVendors || it.name == slot.preset }
            PickerField("${i + 1}", slot.preset,
                filaments.map { PickerItem(it.name, it.name, if (it.system) it.vendor.ifEmpty { "Generic" } else vm.translator.tr("User presets")) },
                { vm.presets.setFilament(i, it) }, Modifier.weight(1f), modified = slot.overrides.isNotEmpty())
            IconButton(onClick = { vm.presets.setActiveFilament(i); vm.presets.openEditor(EditorTarget.Preset(PresetType.FILAMENT)) }) {
                Icon(Icons.Default.Edit, stringResource(R.string.edit))
            }
            if (state.filaments.size > 1) IconButton(onClick = { vm.presets.removeFilament(i) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        }
        if (colorDialog) ColorDialog(slot.color, { vm.presets.setFilamentColor(i, it) }) { colorDialog = false }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = vm.presets::addFilament) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_filament)) }
        if (state.filaments.size > 1) TextButton(onClick = vm.scene::autoFlushMatrix) { Text(stringResource(R.string.flushing_volumes)) }
    }
    if (state.filaments.size > 1) WipeTowerRow(state, vm)

    SectionTitle(stringResource(R.string.process))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PickerField(stringResource(R.string.process), state.print,
            setup?.prints.orEmpty().map { PickerItem(it.name, it.name, if (it.system) "System" else vm.translator.tr("User presets")) },
            vm.presets::selectPrint, Modifier.weight(1f), modified = state.overrides[PresetType.PRINT].orEmpty().isNotEmpty())
        IconButton(onClick = { vm.presets.openEditor(EditorTarget.Preset(PresetType.PRINT)) }) { Icon(Icons.Default.Tune, stringResource(R.string.edit)) }
    }
    QuickSettings(state, vm)
}

/**
 * The favourite options (starred in the settings editor), one tap away, grouped like the settings.
 * Options the desktop rules grey out for the current values are left out here, so dependent
 * settings (e.g. the support type) appear once their parent option is switched on.
 */
@Composable
private fun QuickSettings(state: UiState, vm: AppViewModel) {
    val tr = vm.translator
    val types = PresetType.entries.filter { t -> state.favorites.any { it.startsWith("${t.id}:") } }
    var defs by remember { mutableStateOf<Map<PresetType, Map<String, OptionDef>>>(emptyMap()) }
    LaunchedEffect(types) {
        defs = types.associateWith { t -> runCatching { vm.presets.optionDefs(t) }.getOrDefault(emptyList()).associateBy { it.key } }
    }
    val groups = remember(defs, state.favorites) {
        types.flatMap { t ->
            val d = defs[t] ?: return@flatMap emptyList()
            favoriteGroups(state.favorites, t, buildPages(vm.presets.settingsLayout(t), d), tr).map { g -> Triple(t, g, d) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.favorites), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { vm.presets.openEditor(EditorTarget.Preset(PresetType.PRINT)) }) { Text(stringResource(R.string.edit_favorites)) }
    }
    if (state.favorites.isEmpty()) {
        Text(stringResource(R.string.favorites_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    fun label(d: OptionDef) = tr.tr(d.label.ifEmpty { d.fullLabel.ifEmpty { d.key } })
    val typeName = mapOf(PresetType.FILAMENT to stringResource(R.string.filament), PresetType.PRINTER to stringResource(R.string.printer))

    for ((type, group, typeDefs) in groups) {
        val items = group.options.mapNotNull { k -> typeDefs[k]?.let { d -> state.value(type, k)?.let { QuickOption(type, d, it) } } }
            .filter { type != PresetType.PRINT || (it.def.key !in state.optionStates.hidden && it.def.key !in state.optionStates.disabled) }
        if (items.isEmpty()) continue
        val title = group.title.substringAfterLast(" › ")
        Text(typeName[type]?.let { "$it · $title" } ?: title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        // In the settings' order; consecutive numbers share a row (up to three), so a switch stays
        // above the options that depend on it.
        val rows = mutableListOf<List<QuickOption>>()
        for (f in items) {
            val last = rows.lastOrNull()
            if (f.numeric && last != null && last.all { it.numeric } && last.size < 3) rows[rows.lastIndex] = last + f else rows += listOf(f)
        }
        rows.forEach { row ->
            val f = row.first()
            val d = f.def
            when {
                f.numeric -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { n ->
                        val nd = n.def
                        val decimals = if (nd.baseType == "float") 3 else if (nd.baseType == "percent") 1 else 0
                        val suffix = if (nd.baseType == "percent") "%" else tr.tr(nd.sidetext).takeIf { it.isNotEmpty() && !it.startsWith("%") }
                        NumberField(n.value.removeSuffix("%").toFloat(), { v ->
                            vm.presets.setOption(n.type, nd.key, when (nd.baseType) {
                                "int" -> v.toInt().toString()
                                "percent" -> plain(v) + "%"
                                else -> plain(v)
                            })
                        }, Modifier.weight(1f), label(nd), suffix, decimals)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                d.baseType == "bool" && f.single -> SwitchRow(label(d), f.value == "1") { vm.presets.setOption(f.type, d.key, if (it) "1" else "0") }
                d.enumValues.isNotEmpty() && f.single -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(d), Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) { EnumEditor(d, f.value, true, tr) { vm.presets.setOption(f.type, d.key, it) } }
                }
                else -> OptionRow(d, f.value, state.isModified(f.type, d.key), true, false, tr, { vm.presets.setOption(f.type, d.key, it) }, { vm.presets.resetOption(f.type, d.key) })
            }
        }
    }
}

/** A favourite shown in the print panel, with its current value. */
private class QuickOption(val type: PresetType, val def: OptionDef, val value: String) {
    val single get() = !def.isVector || !value.contains(',')
    val numeric get() = single && def.enumValues.isEmpty() && def.baseType in setOf("int", "float", "percent") && value.removeSuffix("%").toFloatOrNull() != null
}

/** A number without trailing zeros, as the settings store it ("0.12", "15"). */
private fun plain(v: Float): String = java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun WipeTowerRow(state: UiState, vm: AppViewModel) {
    val plate = state.scene.plates.getOrNull(state.activePlate) ?: return
    val pos = plate.wipeTower
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(vm.translator.tr("Prime tower"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (pos != null) {
            NumberField(pos.first, { vm.scene.setWipeTower(plate.index, it to pos.second) }, Modifier.weight(1f), "X", "mm", 1)
            NumberField(pos.second, { vm.scene.setWipeTower(plate.index, pos.first to it) }, Modifier.weight(1f), "Y", "mm", 1)
            IconButton(onClick = { vm.scene.setWipeTower(plate.index, null) }) { Icon(Icons.Default.Delete, stringResource(R.string.reset)) }
        } else {
            TextButton(onClick = { vm.scene.setWipeTower(plate.index, 15f to 150f) }) { Text(stringResource(R.string.set_position)) }
        }
    }
}

private val PALETTE = listOf(
    "#FFFFFF", "#D3D3D3", "#808080", "#202020", "#000000", "#FF7F27", "#FFD700", "#FFFF66",
    "#2FBF4F", "#006400", "#40E0D0", "#2F7FEF", "#00008B", "#8F3FDF", "#FF69B4", "#EF3F3F",
    "#8B0000", "#A0522D", "#D2B48C", "#C0C0C0",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorDialog(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(current ?: "#FF7F27") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.fillMaxWidth()) {
                    PALETTE.forEach { c ->
                        IconButton(onClick = { hex = c }) { ColorDot(c, if (c.equals(hex, true)) 34 else 26) }
                    }
                }
                OutlinedTextField(hex, { hex = it }, label = { Text("Hex") }, singleLine = true, leadingIcon = { ColorDot(hex) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onPick(hex.uppercase()) }, enabled = Regex("#[0-9a-fA-F]{6}").matches(hex)) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Objects -------------------------------------------------------------------------------------

@Composable
private fun ObjectsTab(state: UiState, vm: AppViewModel) {
    val scene = state.scene
    if (scene.isEmpty) {
        Text(stringResource(R.string.no_objects_hint), style = MaterialTheme.typography.bodyMedium)
        return
    }
    if (scene.outsideCount > 0) Text(stringResource(R.string.objects_outside, scene.outsideCount), color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
    scene.objects.forEach { o ->
        val selected = state.selection?.obj == o.index
        Card(
            Modifier.fillMaxWidth().clickable { vm.scene.select(if (selected && state.selection?.volume == -1) null else Selection(o.index)) },
            shape = RoundedCornerShape(12.dp),
            colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        val plates = o.instances.map { it.plate }.distinct().joinToString { if (it < 0) "–" else "${it + 1}" }
                        Text(stringResource(R.string.object_info, o.instances.size, plates, o.triangles), style = MaterialTheme.typography.bodySmall)
                    }
                    if (o.settings.overrides.isNotEmpty()) Icon(Icons.Default.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                if (selected) ObjectDetails(state, vm, o.index)
            }
        }
    }
}

@Composable
private fun ObjectDetails(state: UiState, vm: AppViewModel, objIndex: Int) {
    val o = state.scene.objects[objIndex]
    val sel = state.selection ?: return
    val inst = o.instances.getOrNull(sel.instance) ?: return
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (o.instances.size > 1) {
            PickerField(stringResource(R.string.instance), sel.instance.toString(),
                o.instances.map { PickerItem(it.index.toString(), "#${it.index + 1}") }, { vm.scene.select(sel.copy(instance = it.toInt(), volume = -1)) })
        }
        Vec3Fields(stringResource(R.string.position), inst.offset.x, inst.offset.y, inst.offset.z, "mm", { x, y, z -> vm.scene.setTransform(offset = Vec3(x, y, z)) })
        Vec3Fields(stringResource(R.string.rotation), inst.rotation.x, inst.rotation.y, inst.rotation.z, "°", { x, y, z -> vm.scene.setTransform(rotation = Vec3(x, y, z)) }, 1)
        Vec3Fields(stringResource(R.string.scale), inst.scale.x * 100, inst.scale.y * 100, inst.scale.z * 100, "%",
            { x, y, z -> vm.scene.setTransform(scale = Vec3(x / 100, y / 100, z / 100)) }, 1)
        Vec3Fields(stringResource(R.string.size), inst.size.x, inst.size.y, inst.size.z, "mm", { x, y, z ->
            vm.scene.setTransform(scale = Vec3(inst.scale.x * x / inst.size.x.coerceAtLeast(1e-3f), inst.scale.y * y / inst.size.y.coerceAtLeast(1e-3f),
                inst.scale.z * z / inst.size.z.coerceAtLeast(1e-3f)))
        })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mirror), style = MaterialTheme.typography.labelMedium)
            listOf("X", "Y", "Z").forEachIndexed { axis, label ->
                OutlinedButton(onClick = {
                    val m = inst.mirror
                    vm.scene.setTransform(mirror = when (axis) { 0 -> m.copy(x = -m.x); 1 -> m.copy(y = -m.y); else -> m.copy(z = -m.z) })
                }) { Text(label) }
            }
        }
        if (state.filaments.size > 1) {
            val ext = o.settings["extruder"] ?: "1"
            PickerField(stringResource(R.string.filament), ext,
                state.filaments.mapIndexed { i, f -> PickerItem((i + 1).toString(), "${i + 1}: ${f.preset}") },
                { vm.scene.setObjectSetting(objIndex, -1, "extruder", it) })
        }
        FilledTonalButton(onClick = { vm.presets.openEditor(EditorTarget.Object(objIndex)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Tune, null); Text(" " + stringResource(R.string.object_settings, o.settings.overrides.size))
        }

        if (o.volumes.size > 1) {
            HorizontalDivider()
            Text(stringResource(R.string.parts_and_modifiers), style = MaterialTheme.typography.labelLarge)
            o.volumes.forEach { v ->
                val vSel = sel.volume == v.index
                Row(Modifier.fillMaxWidth().clickable { vm.scene.select(sel.copy(volume = if (vSel) -1 else v.index)) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(volumeLabel(v.type) + " · " + v.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = if (vSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (v.type == VolumeType.PART || v.type == VolumeType.MODIFIER) {
                        IconButton(onClick = { vm.presets.openEditor(EditorTarget.Object(objIndex, v.index)) }) { Icon(Icons.Default.Tune, stringResource(R.string.settings)) }
                    }
                    IconButton(onClick = { vm.scene.select(sel.copy(volume = v.index)); vm.scene.deleteSelected() }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                }
            }
        }

        HorizontalDivider()
        LayerRanges(vm, objIndex, o.layerRanges, inst.size.z)
    }
}

@Composable
private fun volumeLabel(t: VolumeType) = stringResource(
    when (t) {
        VolumeType.PART -> R.string.vol_part
        VolumeType.NEGATIVE -> R.string.vol_negative
        VolumeType.MODIFIER -> R.string.vol_modifier
        VolumeType.SUPPORT_BLOCKER -> R.string.vol_blocker
        VolumeType.SUPPORT_ENFORCER -> R.string.vol_enforcer
    }
)

/** Height ranges with their own settings (desktop: "Height range modifier"). */
@Composable
private fun LayerRanges(vm: AppViewModel, obj: Int, ranges: List<LayerRange>, height: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.height_ranges), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = {
            val from = ranges.maxOfOrNull { it.to } ?: 0f
            vm.scene.setLayerRanges(obj, ranges + LayerRange(from, (from + 2f).coerceAtMost(height.coerceAtLeast(from + 0.2f)), emptyMap()))
        }) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
    }
    ranges.forEachIndexed { i, r ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberField(r.from, { v -> vm.scene.setLayerRanges(obj, ranges.mapIndexed { j, x -> if (j == i) x.copy(from = v) else x }) }, Modifier.weight(1f), stringResource(R.string.from), "mm")
            NumberField(r.to, { v -> vm.scene.setLayerRanges(obj, ranges.mapIndexed { j, x -> if (j == i) x.copy(to = v) else x }) }, Modifier.weight(1f), stringResource(R.string.to), "mm")
            IconButton(onClick = { vm.presets.openEditor(EditorTarget.Range(obj, i)) }) { Icon(Icons.Default.Tune, stringResource(R.string.settings)) }
            IconButton(onClick = { vm.scene.setLayerRanges(obj, ranges.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        }
    }
}
