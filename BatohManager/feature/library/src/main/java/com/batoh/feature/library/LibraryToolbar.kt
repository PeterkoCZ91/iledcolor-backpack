package com.batoh.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifDisplayName
import com.batoh.core.domain.model.GifNameValidation

/** Search field, sort menu and the "count · size" summary above the collection grid. */
@Composable
internal fun LibraryToolbar(
    state: LibraryUiState.Success,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Local text state keeps typing synchronous; the ViewModel filters from the pushed value.
    var text by rememberSaveable { mutableStateOf(state.query) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val summary = state.summary

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            label = { Text(stringResource(R.string.library_search_label)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        onQueryChange("")
                    }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.library_search_clear)) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val shownSize = android.text.format.Formatter.formatShortFileSize(context, summary.shownBytes)
            Text(
                text = if (summary.filtered) {
                    stringResource(R.string.library_summary_filtered, summary.shownCount, summary.totalCount, shownSize)
                } else stringResource(R.string.library_summary_all, summary.totalCount, shownSize),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                val sortLabel = stringResource(state.sort.labelRes())
                val sortDescription = stringResource(R.string.library_sort_button_description, sortLabel)
                TextButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.semantics { contentDescription = sortDescription }
                ) {
                    Text(stringResource(R.string.library_sort_button, sortLabel))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    LibrarySort.values().forEach { option ->
                        val isSelected = option == state.sort
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            onClick = {
                                sortMenuOpen = false
                                onSortChange(option)
                            },
                            leadingIcon = {
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            modifier = Modifier.semantics { selected = isSelected }
                        )
                    }
                }
            }
        }
    }
}

/** Rename dialog with live validation; the `.gif` extension is shown as a fixed suffix. */
@Composable
internal fun RenameGifDialog(
    gif: Gif,
    renaming: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(gif.id) { mutableStateOf(GifDisplayName.baseName(gif.title)) }
    val validation = GifDisplayName.validate(text)
    val problem = (validation as? GifNameValidation.Invalid)?.problem
    val errorText = problem?.let { stringResource(it.messageRes()) }
    val canConfirm = validation is GifNameValidation.Valid && !renaming
    val confirm = {
        if (validation is GifNameValidation.Valid) {
            // An unchanged name needs no MediaStore write.
            if (validation.fileName == gif.title) onDismiss() else onConfirm(text)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(GifDisplayName.MAX_BASE_LENGTH * 2) },
                label = { Text(stringResource(R.string.library_rename_label)) },
                singleLine = true,
                suffix = { Text(GifDisplayName.EXTENSION) },
                isError = errorText != null,
                supportingText = { Text(errorText ?: stringResource(R.string.library_rename_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) confirm() }),
                modifier = Modifier.fillMaxWidth().semantics {
                    if (errorText != null) error(errorText)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = canConfirm) {
                Text(stringResource(R.string.library_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_rename_cancel)) }
        }
    )
}
