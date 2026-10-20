package app.orcaandroid.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import app.orcaandroid.core.OptionDef
import app.orcaandroid.core.overrides
import app.orcaandroid.core.PresetType
import app.orcaandroid.core.SettingsGroup
import app.orcaandroid.core.SettingsPage
import app.orcaandroid.core.Translator
import app.orcaandroid.ui.AppViewModel
import app.orcaandroid.ui.EditorTarget
import app.orcaandroid.ui.UiState
import app.orcaandroid.ui.WIDE_LAYOUT
import app.orcaandroid.ui.components.ConfirmDialog
import app.orcaandroid.ui.components.PickerDialog
import app.orcaandroid.ui.components.PickerItem
import app.orcaandroid.ui.components.TextInputDialog
import app.orcaandroid.ui.prepare.ColorDot

/** Where the edited values come from and go to: a preset, an object/part, or a height range. */
private class EditSource(
    val type: PresetType,
    val value: (String) -> String?,
    val isModified: (String) -> Boolean,
    val set: (String, String) -> Unit,
    val reset: (String) -> Unit,
    val modifiedCount: Int,
)

@Composable
fun SettingsEditor(state: UiState, vm: AppViewModel) {
    val target = state.editor ?: return
    androidx.activity.compose.BackHandler(onBack = vm.presets::closeEditor)
    when (target) {
        is EditorTarget.Preset -> PresetEditor(state, vm, target.type)
        is EditorTarget.Object -> {
            val o = state.scene.objects.getOrNull(target.obj) ?: run { LaunchedEffect(Unit) { vm.presets.closeEditor() }; return }
            val settings = (if (target.volume >= 0) o.volumes.getOrNull(target.volume)?.settings.orEmpty() else o.settings).overrides
            val title = if (target.volume >= 0) o.volumes.getOrNull(target.volume)?.name.orEmpty() else o.name
            val source = EditSource(
                PresetType.PRINT,
                value = { k -> settings[k] ?: state.value(PresetType.PRINT, k) },
                isModified = { k -> settings.containsKey(k) },
                set = { k, v -> vm.scene.setObjectSetting(target.obj, target.volume, k, v) },
                reset = { k -> vm.scene.setObjectSetting(target.obj, target.volume, k, null) },
                modifiedCount = settings.size,
            )
            EditorFrame(vm, state, stringResource(R.string.object_settings_title), title, source, objectMode = true)
        }
        is EditorTarget.Range -> {
            val o = state.scene.objects.getOrNull(target.obj)
            val r = o?.layerRanges?.getOrNull(target.range) ?: run { LaunchedEffect(Unit) { vm.presets.closeEditor() }; return }
            val source = EditSource(
                PresetType.PRINT,
                value = { k -> r.settings[k] ?: o.settings[k] ?: state.value(PresetType.PRINT, k) },
                isModified = { k -> k in r.settings.overrides },
                set = { k, v -> vm.scene.setRangeSetting(target.obj, target.range, k, v) },
                reset = { k -> vm.scene.setRangeSetting(target.obj, target.range, k, null) },
                modifiedCount = r.settings.overrides.size,
            )
            EditorFrame(vm, state, stringResource(R.string.range_settings_title), "${o.name} · %.2f–%.2f mm".format(java.util.Locale.ROOT, r.from, r.to),
                source, objectMode = true)
        }
        is EditorTarget.Compare -> CompareView(vm, target)
    }
}

private val TYPE_TITLES = mapOf(PresetType.PRINT to R.string.process, PresetType.FILAMENT to R.string.filament, PresetType.PRINTER to R.string.printer)

@Composable
private fun PresetEditor(state: UiState, vm: AppViewModel, type: PresetType) {
    var saveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var comparePick by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.presets.exportPreset(type, u) } }
    val source = EditSource(
        type,
        value = { k -> state.value(type, k) },
        isModified = { k -> state.isModified(type, k) },
        set = { k, v -> vm.presets.setOption(type, k, v) },
        reset = { k -> vm.presets.resetOption(type, k) },
        modifiedCount = state.overridesOf(type).size,
    )
    val name = state.presetName(type).orEmpty()
    EditorFrame(vm, state, stringResource(R.string.settings), name, source, objectMode = false,
        tabs = {
            PrimaryTabRow(selectedTabIndex = type.ordinal) {
                PresetType.entries.forEach { t ->
                    Tab(t == type, { vm.presets.openEditor(EditorTarget.Preset(t)) }, text = { Text(stringResource(TYPE_TITLES.getValue(t))) })
                }
            }
            if (type == PresetType.FILAMENT && state.filaments.size > 1) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.filaments.forEachIndexed { i, f ->
                        FilterChip(i == state.activeFilament, { vm.presets.setActiveFilament(i) }, label = { Text("${i + 1}") }, leadingIcon = { ColorDot(f.color) })
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = { saveDialog = true }) { Text(stringResource(R.string.save_as)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (source.modifiedCount > 0) DropdownMenuItem(text = { Text(stringResource(R.string.reset_all)) }, onClick = { menu = false; vm.presets.resetAll(type) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.compare_with)) }, onClick = { menu = false; comparePick = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_preset)) }, onClick = { menu = false; export.launch("$name.json") })
                    if (vm.presets.isUserPreset(type)) DropdownMenuItem(text = { Text(stringResource(R.string.delete_preset)) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        })

    if (saveDialog) TextInputDialog(stringResource(R.string.save_preset_title), suggestUserName(name), stringResource(R.string.name),
        { vm.presets.savePresetAs(type, it) }) { saveDialog = false }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.delete_preset), stringResource(R.string.delete_preset_text, name), stringResource(R.string.delete),
        { vm.presets.deleteSelectedPreset(type) }) { confirmDelete = false }
    if (comparePick) {
        val items = when (type) {
            PresetType.PRINT -> state.setup?.prints.orEmpty().map { PickerItem(it.name, it.name, it.vendor) }
            PresetType.FILAMENT -> state.setup?.filaments.orEmpty().map { PickerItem(it.name, it.name, it.vendor) }
            PresetType.PRINTER -> state.printers.map { PickerItem(it.name, it.name, it.vendor) }
        }.filter { it.id != name }
        PickerDialog(stringResource(R.string.compare_with), null, items, onDismiss = { comparePick = false }) {
            comparePick = false
            vm.presets.openEditor(EditorTarget.Compare(type, name, it))
        }
    }
}

@Composable
private fun EditorFrame(
    vm: AppViewModel,
    state: UiState,
    title: String,
    subtitle: String,
    source: EditSource,
    objectMode: Boolean,
    tabs: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val type = source.type
    val tr = vm.translator
    var defs by remember(type) { mutableStateOf<Map<String, OptionDef>>(emptyMap()) }
    LaunchedEffect(type) {
        runCatching { vm.presets.optionDefs(type) }.onSuccess { list -> defs = list.associateBy { it.key } }
            .onFailure { vm.showError(it.message ?: it.toString()) }
    }
    val pages = remember(type, defs) { if (defs.isEmpty()) emptyList() else buildPages(vm.presets.settingsLayout(type), defs) }
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var query by remember(type) { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var pageIndex by rememberSaveable(type) { mutableIntStateOf(0) }
    // Groups start collapsed; the ones the user opens stay open (per page) while the editor is shown.
    val expanded = remember(type) { mutableStateMapOf<String, Boolean>() }
    // Per-object editors start with only what the object overrides; "all" shows every option.
    var onlyModified by remember { mutableStateOf(objectMode && source.modifiedCount > 0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm.presets::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle + if (source.modifiedCount > 0) "  ·  " + stringResource(R.string.n_modified, source.modifiedCount) else "",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                actions()
            }
            tabs()
            if (searching) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.search_setting)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f, fill = false)) {
                    listOf(R.string.mode_simple, R.string.mode_advanced, R.string.mode_expert).forEachIndexed { i, label ->
                        SegmentedButton(mode == i, { mode = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                if (objectMode) FilterChip(onlyModified, { onlyModified = !onlyModified }, label = { Text(stringResource(R.string.only_changed)) })
            }
            HorizontalDivider()

            // Starred options of this preset type form a first page, independent of the mode.
            val favKeys = state.favorites.filter { it.startsWith("${type.id}:") }.map { it.substringAfter(':') }.filter { it in defs }
            val favTitle = stringResource(R.string.favorites)
            val shownPages = if (favKeys.isEmpty()) pages else listOf(SettingsPage(favTitle, listOf(SettingsGroup("", favKeys)))) + pages
            val onFavPage = favKeys.isNotEmpty() && pageIndex == 0
            fun visible(def: OptionDef) = onFavPage || def.mode <= mode || (mode == 2 && def.mode == 3)
            val hidden = state.optionStates.hidden
            val groups: List<SettingsGroup> = when {
                query.isNotBlank() -> {
                    val q = query.trim().lowercase()
                    pages.flatMap { page ->
                        page.groups.mapNotNull { g ->
                            val hits = g.options.filter { key ->
                                val d = defs[key] ?: return@filter false
                                listOf(key, d.label, d.fullLabel, tr.tr(d.label), tr.tr(d.fullLabel), tr.tr(d.tooltip)).any { it.lowercase().contains(q) }
                            }
                            if (hits.isEmpty()) null else SettingsGroup("${tr.tr(page.title)} › ${tr.tr(g.title)}", hits)
                        }
                    }
                }
                onlyModified -> pages.flatMap { page ->
                    page.groups.mapNotNull { g ->
                        val hits = g.options.filter(source.isModified)
                        if (hits.isEmpty()) null else SettingsGroup("${tr.tr(page.title)} › ${tr.tr(g.title)}", hits)
                    }
                }
                else -> shownPages.getOrNull(pageIndex)?.groups.orEmpty()
            }
            val showPages = query.isBlank() && !onlyModified

            if (!wide && showPages && shownPages.isNotEmpty()) {
                PrimaryScrollableTabRow(selectedTabIndex = pageIndex.coerceIn(0, shownPages.lastIndex), edgePadding = 8.dp) {
                    shownPages.forEachIndexed { i, p -> Tab(i == pageIndex, { pageIndex = i }, text = { Text(tr.tr(p.title)) }) }
                }
            }
            Row(Modifier.fillMaxSize()) {
                if (wide && showPages) {
                    LazyColumn(Modifier.width(220.dp).fillMaxHeight().padding(vertical = 8.dp)) {
                        itemsIndexed(shownPages) { i, page ->
                            val selected = i == pageIndex
                            Text(
                                tr.tr(page.title),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                                    .clickable { pageIndex = i }.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)) {
                    groups.forEachIndexed { gi, group ->
                        val options = group.options.mapNotNull { defs[it] }.filter {
                            (query.isNotBlank() || onlyModified || visible(it)) && it.key !in hidden && source.value(it.key) != null
                        }
                        if (options.isEmpty()) return@forEachIndexed
                        // Search hits and the "only changed" list are shown open, or they would be hidden.
                        val groupId = "$pageIndex:${group.title}"
                        val open = group.title.isEmpty() || !showPages || expanded[groupId] == true
                        if (group.title.isNotEmpty()) item(key = "g:$gi:${group.title}") {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = showPages) { expanded[groupId] = !open }.padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (showPages) tr.tr(group.title) else group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                val changed = options.count { source.isModified(it.key) }
                                if (changed > 0 && !open) Text(stringResource(R.string.n_modified, changed), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 8.dp))
                                if (showPages) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    stringResource(if (open) R.string.collapse else R.string.expand), tint = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                        if (open) items(options, key = { "o:$gi:${it.key}" }) { def ->
                            OptionRow(def, source.value(def.key).orEmpty(), source.isModified(def.key), def.key !in state.optionStates.disabled,
                                wide, tr, { source.set(def.key, it) }, { source.reset(def.key) },
                                favorite = "${type.id}:${def.key}" in state.favorites, onFavorite = { vm.presets.toggleFavorite(type, def.key) })
                        }
                    }
                    item { Spacer(Modifier.heightIn(min = 48.dp)) }
                }
            }
        }
    }
}

/** Desktop layout plus a trailing page for options the layout does not place. */
private fun buildPages(layout: List<SettingsPage>, defs: Map<String, OptionDef>): List<SettingsPage> {
    val placed = layout.flatMap { p -> p.groups.flatMap { it.options } }.toSet()
    val rest = defs.values.filter { it.key !in placed && !it.readonly }
    val extra = rest.groupBy { it.category.ifEmpty { "Others" } }.toSortedMap()
        .map { (category, list) -> SettingsGroup(category, list.map { it.key }) }
    if (extra.isEmpty()) return layout
    // Append to the desktop's own "Others" page where there is one.
    val others = layout.indexOfLast { it.title == "Others" }
    return if (others < 0) layout + SettingsPage("Others", extra)
    else layout.mapIndexed { i, p -> if (i == others) p.copy(groups = p.groups + extra) else p }
}

private fun suggestUserName(base: String): String = base.substringBefore(" @").trim().ifEmpty { base } + " - Custom"

@Composable
internal fun OptionRow(
    def: OptionDef,
    value: String,
    modified: Boolean,
    enabled: Boolean,
    wide: Boolean,
    tr: Translator,
    onChange: (String) -> Unit,
    onReset: () -> Unit,
    favorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
) {
    var showTooltip by remember { mutableStateOf(false) }
    val label: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (modified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface, CircleShape))
            Text(tr.tr(def.label.ifEmpty { def.fullLabel.ifEmpty { def.key } }), Modifier.padding(start = 8.dp).clickable(enabled = def.tooltip.isNotEmpty()) { showTooltip = true },
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
    val trailing: @Composable () -> Unit = {
        if (wide) IconButton(onClick = { showTooltip = true }, enabled = def.tooltip.isNotEmpty()) { Icon(Icons.Default.Info, stringResource(R.string.info)) }
        IconButton(onClick = onReset, enabled = modified) { Icon(Icons.Default.Refresh, stringResource(R.string.reset)) }
        if (onFavorite != null) IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
        }
    }
    if (wide) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            label(Modifier.weight(1f).padding(end = 12.dp))
            Box(Modifier.width(300.dp)) { OptionEditor(def, value, enabled, tr, onChange) }
            trailing()
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            label(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(start = 16.dp)) { OptionEditor(def, value, enabled, tr, onChange) }
                trailing()
            }
        }
    }
    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            title = { Text(tr.tr(def.fullLabel.ifEmpty { def.label })) },
            text = { Text(tr.tr(def.tooltip) + "\n\n" + def.key, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showTooltip = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
internal fun OptionEditor(def: OptionDef, value: String, enabled: Boolean, tr: Translator, onChange: (String) -> Unit) {
    // Vector options with several distinct entries (e.g. per-extruder values) are edited as raw text.
    val single = !def.isVector || !value.contains(',')
    when {
        def.readonly -> Text(value, style = MaterialTheme.typography.bodyMedium)
        def.baseType == "bool" && single -> Switch(checked = value == "1", onCheckedChange = { onChange(if (it) "1" else "0") }, enabled = enabled)
        def.enumValues.isNotEmpty() && single -> EnumEditor(def, value, enabled, tr, onChange)
        def.isCode || def.multiline -> CodeEditor(tr.tr(def.label), value, enabled, onChange)
        else -> TextEditor(def, value, enabled, tr, onChange)
    }
}

@Composable
internal fun EnumEditor(def: OptionDef, value: String, enabled: Boolean, tr: Translator, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val index = def.enumValues.indexOf(value)
    val label = if (index >= 0) tr.tr(def.enumLabels.getOrElse(index) { value }) else value
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            def.enumValues.forEachIndexed { i, v ->
                DropdownMenuItem(text = { Text(tr.tr(def.enumLabels.getOrElse(i) { v })) }, onClick = { open = false; onChange(v) })
            }
        }
    }
}

/** Text field that commits on "done" or when focus leaves, so each keystroke does not trigger work. */
@Composable
private fun TextEditor(def: OptionDef, value: String, enabled: Boolean, tr: Translator, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val numeric = def.baseType in setOf("int", "float", "percent", "float_or_percent")
    var focused by remember { mutableStateOf(false) }
    val commit = { if (text != value) onChange(text.trim()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        enabled = enabled,
        suffix = if (def.sidetext.isNotEmpty() && !def.sidetext.startsWith("%")) { { Text(tr.tr(def.sidetext), maxLines = 1) } } else null,
        isError = numeric && !isValidNumber(def, text.trim()),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric && !def.isVector) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (focused && !it.isFocused) commit(); focused = it.isFocused },
    )
}

private fun isValidNumber(def: OptionDef, text: String): Boolean {
    if (def.isVector) return true
    val number = text.removeSuffix("%").toDoubleOrNull() ?: return false
    if (text.endsWith("%") && def.baseType !in setOf("percent", "float_or_percent")) return false
    if (!text.endsWith("%")) {
        def.min?.let { if (number < it) return false }
        def.max?.let { if (number > it) return false }
    }
    return true
}

/** Multi-line values (G-code, notes, post-processing) open a full-size editor. */
@Composable
private fun CodeEditor(title: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val empty = stringResource(R.string.empty)
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(unescape(value).lineSequence().firstOrNull()?.ifEmpty { empty } ?: empty, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        // Values are stored escaped ("\n" as two characters); edit them as real lines.
        var text by remember { mutableStateOf(unescape(value)) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(text, { text = it }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 600.dp))
            },
            confirmButton = { TextButton(onClick = { open = false; onChange(escape(text)) }) { Text(stringResource(R.string.apply)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Inverse of libslic3r's escape_string_cstyle for the characters G-code templates use. */
private fun unescape(s: String): String {
    val v = if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s.substring(1, s.length - 1) else s
    val out = StringBuilder()
    var i = 0
    while (i < v.length) {
        val c = v[i]
        if (c == '\\' && i + 1 < v.length) {
            when (v[i + 1]) {
                'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                '\\' -> out.append('\\'); '"' -> out.append('"')
                else -> out.append(c).append(v[i + 1])
            }
            i += 2
        } else {
            out.append(c); i++
        }
    }
    return out.toString()
}

private fun escape(s: String): String =
    s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"").replace("\t", "\\t")

/** Two presets side by side, differing options only (desktop: "Compare presets"). */
@Composable
private fun CompareView(vm: AppViewModel, target: EditorTarget.Compare) {
    val tr = vm.translator
    var defs by remember { mutableStateOf<Map<String, OptionDef>>(emptyMap()) }
    var left by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var right by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(target) {
        runCatching {
            defs = vm.presets.optionDefs(target.type).associateBy { it.key }
            left = vm.presets.presetValuesOf(target.type, target.left)
            right = vm.presets.presetValuesOf(target.type, target.right)
        }.onFailure { vm.showError(it.message ?: it.toString()) }
    }
    val diff = remember(left, right) {
        (left.keys + right.keys).filter { left[it] != right[it] && defs[it]?.readonly != true }.sortedBy { defs[it]?.category + it }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm.presets::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.compare_presets) + " · " + stringResource(R.string.n_differences, diff.size), style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(target.left, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(target.right, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(diff, key = { it }) { key ->
                val d = defs[key]
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(tr.tr(d?.let { it.label.ifEmpty { it.fullLabel } } ?: key), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        Text(unescape(left[key] ?: "—").take(200), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(unescape(right[key] ?: "—").take(200), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
