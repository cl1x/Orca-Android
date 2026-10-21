package app.orcaandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import app.orcaandroid.ui.prepare.PrepareSummary
import app.orcaandroid.ui.preview.PreviewSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import app.orcaandroid.render.PlateView
import app.orcaandroid.ui.components.Viewport
import app.orcaandroid.ui.device.DeviceScreen
import app.orcaandroid.ui.prepare.PrepareOverlay
import app.orcaandroid.ui.prepare.PreparePanel
import app.orcaandroid.ui.preview.PreviewOverlay
import app.orcaandroid.ui.preview.PreviewPanel
import app.orcaandroid.ui.settings.MoreScreen
import app.orcaandroid.ui.settings.PrinterSetupScreen
import app.orcaandroid.ui.settings.SettingsEditor

private data class Destination(val screen: Screen, val label: Int, val icon: ImageVector)

private val DESTINATIONS = listOf(
    Destination(Screen.PREPARE, R.string.tab_prepare, Icons.Outlined.ViewInAr),
    Destination(Screen.PREVIEW, R.string.tab_preview, Icons.Outlined.Layers),
    Destination(Screen.DEVICE, R.string.tab_device, Icons.Outlined.Print),
    Destination(Screen.MORE, R.string.tab_more, Icons.Outlined.MoreHoriz),
)

/** Width from which the app uses the side-by-side tablet layout. */
val WIDE_LAYOUT = 840.dp

@Composable
fun AppScaffold(state: UiState, vm: AppViewModel) {
    when {
        state.phase == Phase.LOADING -> Busy(state.busy ?: stringResource(R.string.starting))
        state.phase == Phase.SETUP || state.showPrinterSetup -> PrinterSetupScreen(state, vm)
        state.editor != null -> SettingsEditor(state, vm)
        else -> MainLayout(state, vm)
    }
    GlobalDialogs(state, vm)
}

/** Phone navigation: icons only, the active destination as a labelled pill (lower than NavigationBar). */
@Composable
private fun CompactNavigationBar(state: UiState, vm: AppViewModel) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            DESTINATIONS.forEach { d ->
                val selected = state.screen == d.screen
                val label = stringResource(d.label)
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .selectable(selected, role = Role.Tab) { vm.setScreen(d.screen) }
                        .padding(horizontal = if (selected) 14.dp else 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(d.icon, if (selected) null else label,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun MainLayout(state: UiState, vm: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT
        val content: @Composable (Modifier) -> Unit = { m ->
            Box(m.clipToBounds()) {
                when (state.screen) {
                    Screen.PREPARE, Screen.PREVIEW -> Workspace(state, vm, wide)
                    Screen.DEVICE -> DeviceScreen(state, vm)
                    Screen.MORE -> MoreScreen(state, vm)
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    DESTINATIONS.forEach { d ->
                        NavigationRailItem(
                            selected = state.screen == d.screen,
                            onClick = { vm.setScreen(d.screen) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(stringResource(d.label)) },
                        )
                    }
                }
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                CompactNavigationBar(state, vm)
            }
        }
    }
}

/**
 * Prepare and Preview share one 3D view. Tablets show the settings/preview panel beside it,
 * phones in a bottom sheet so the view keeps the full screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Workspace(state: UiState, vm: AppViewModel, wide: Boolean) {
    var plateView by remember { mutableStateOf<PlateView?>(null) }
    val viewport: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            Viewport(state, vm, onView = { plateView = it }, modifier = Modifier.fillMaxSize())
            if (state.screen == Screen.PREPARE) PrepareOverlay(state, vm, plateView, wide)
            else PreviewOverlay(state, vm, plateView, wide)
            state.projectSettingsPrompt?.let { p ->
                ProjectSettingsBanner(p, vm, Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 60.dp, end = 12.dp))
            }
            if (state.restoringSession) Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.restoring_session), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.width(200.dp))
                }
            }
        }
    }
    // The summary line (what is set up, and slice / print) heads the panel in both layouts.
    val panel: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (state.screen == Screen.PREPARE) PrepareSummary(state, vm) else PreviewSummary(state, vm)
            }
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                if (state.screen == Screen.PREPARE) PreparePanel(state, vm, wide) else PreviewPanel(state, vm, wide)
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            viewport(Modifier.weight(1f).fillMaxHeight())
            Surface(Modifier.width(380.dp).fillMaxHeight(), tonalElevation = 1.dp) { panel() }
        }
    } else {
        // Collapsed, the sheet is one line: what is set up and the main action (slice / print).
        // Pulled up, it shows the full panel.
        val sheet = rememberBottomSheetScaffoldState()
        BottomSheetScaffold(
            scaffoldState = sheet,
            sheetPeekHeight = 84.dp,
            sheetDragHandle = {
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(32.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            },
            sheetContent = { Box(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) { panel() } },
        ) { padding ->
            viewport(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()))
        }
    }
}

/**
 * Asks whether a loaded project's settings replace the current ones. A banner over the view, not a
 * dialog, so the project's models stay visible while deciding.
 */
@Composable
private fun ProjectSettingsBanner(p: ProjectSettingsPrompt, vm: AppViewModel, modifier: Modifier) {
    Surface(modifier.widthIn(max = 560.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.project_settings_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.project_settings_text), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            @Composable fun change(label: Int, c: Pair<String, String>?) = c?.let {
                Text("${stringResource(label)}: ${it.first.ifEmpty { "–" }}  →  ${it.second}", style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
            }
            change(R.string.printer, p.printerChange)
            change(R.string.process, p.printChange)
            change(R.string.filaments, p.filamentChange)
            if (p.changedSettings > 0) Text(stringResource(R.string.project_settings_changed, p.changedSettings), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm.files::keepCurrentSettings) { Text(stringResource(R.string.keep_current_settings)) }
                TextButton(onClick = vm.files::useProjectSettings) { Text(stringResource(R.string.use_project_settings)) }
            }
        }
    }
}

@Composable
private fun GlobalDialogs(state: UiState, vm: AppViewModel) {
    if (state.busy != null && state.phase != Phase.LOADING) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(state.busy)
                }
            },
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(stringResource(R.string.error)) },
            // Engine errors are OrcaSlicer's (translatable) messages; show them in the app's language.
            text = { Text(vm.translator.tr(message)) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.ok)) } },
        )
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar, Modifier.padding(bottom = 72.dp))
    }
}

@Composable
fun Busy(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
