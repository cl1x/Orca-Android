package app.orcaandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcaandroid.R
import java.util.Locale

/** An entry in a [PickerField]; [group] is shown as a section header (e.g. the vendor). */
data class PickerItem(val id: String, val label: String = id, val group: String = "")

/**
 * The items of a picker matching every word of [query] (in label or group), grouped so each group
 * gets one heading even when [items] interleave groups (groups in first-seen order), without
 * duplicates (a duplicate would crash the list, whose keys must be unique).
 */
internal fun pickerEntries(items: List<PickerItem>, query: String): List<PickerItem> {
    val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    return items.filter { item -> words.all { w -> item.label.lowercase().contains(w) || item.group.lowercase().contains(w) } }
        .groupBy { it.group }.values.flatten()
        .distinctBy { it.group to it.id }
}

/** A compact labelled field that opens a searchable single-choice dialog. */
@Composable
fun PickerField(
    label: String,
    selected: String?,
    items: List<PickerItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    modified: Boolean = false,
    caption: String? = label,
    extraAction: Pair<String, () -> Unit>? = null,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(modifier = modifier.fillMaxWidth().clickable(enabled = enabled && items.isNotEmpty()) { open = true }) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            // Without a caption (a section title already names the field) only "modified" is shown.
            val text = listOfNotNull(caption, if (modified) stringResource(R.string.modified) else null).joinToString(" · ")
            if (text.isNotEmpty()) Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = if (modified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Text(
                items.firstOrNull { it.id == selected }?.label ?: selected ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (open) PickerDialog(label, selected, items, onDismiss = { open = false },
        extraAction = extraAction?.let { (text, action) -> text to { open = false; action() } }) { open = false; onSelect(it) }
}

@Composable
fun PickerDialog(
    title: String,
    selected: String?,
    items: List<PickerItem>,
    onDismiss: () -> Unit,
    extraAction: Pair<String, () -> Unit>? = null,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, items) { pickerEntries(items, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (items.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                    var lastGroup: String? = null
                    filtered.forEach { item ->
                        if (item.group.isNotEmpty() && item.group != lastGroup) {
                            lastGroup = item.group
                            item(key = "g:${item.group}") {
                                Text(item.group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                HorizontalDivider()
                            }
                        }
                        item(key = "i:${item.group}:${item.id}") {
                            Row(Modifier.fillMaxWidth().clickable { onPick(item.id) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = item.id == selected, onClick = { onPick(item.id) })
                                Text(item.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { extraAction?.let { (text, action) -> TextButton(onClick = action) { Text(text) } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Numeric text field that commits on "done" or focus loss (so typing does not trigger work). */
@Composable
fun NumberField(
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    suffix: String? = null,
    decimals: Int = 2,
) {
    val formatted = "%.${decimals}f".format(Locale.ROOT, value + 0f)
        .let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
        .let { if (it == "-0") "0" else it }
    var text by remember(value) { mutableStateOf(formatted) }
    var focused by remember { mutableStateOf(false) }
    // Only real edits count: the field shows a rounded value, which must not be written back.
    val commit = { if (text != formatted) text.replace(',', '.').toFloatOrNull()?.let { if (it != value) onCommit(it) } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = label?.let { { Text(it, maxLines = 1) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = text.replace(',', '.').toFloatOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier.onFocusChanged { if (focused && !it.isFocused) commit(); focused = it.isFocused },
    )
}

/** Three numeric fields in a row (x / y / z). */
@Composable
fun Vec3Fields(label: String, x: Float, y: Float, z: Float, suffix: String, onCommit: (Float, Float, Float) -> Unit, decimals: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(x, { onCommit(it, y, z) }, Modifier.weight(1f), "X", suffix, decimals)
            NumberField(y, { onCommit(x, it, z) }, Modifier.weight(1f), "Y", suffix, decimals)
            NumberField(z, { onCommit(x, y, it) }, Modifier.weight(1f), "Z", suffix, decimals)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Formats seconds as "2 h 5 min" / "5 min 3 s". */
fun formatDuration(seconds: Double): String {
    val s = seconds.toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min ${s % 60} s"
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
