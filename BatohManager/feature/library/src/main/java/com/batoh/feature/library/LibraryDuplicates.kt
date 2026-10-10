package com.batoh.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.batoh.core.domain.model.Gif

/**
 * "Find duplicates" dialog: scanning progress, then groups of identical GIFs. The first GIF of
 * each group is always kept; the extra copies are pre-checked and deleted only after a second confirmation.
 */
@Composable
internal fun DuplicatesDialog(
    state: DuplicateScanState,
    onDelete: (List<Gif>) -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        DuplicateScanState.Scanning -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.library_duplicates_action)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.library_duplicates_scanning))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicates_cancel)) }
            }
        )
        is DuplicateScanState.Done -> DuplicateResults(state.groups, onDelete, onDismiss)
    }
}

@Composable
private fun DuplicateResults(groups: List<List<Gif>>, onDelete: (List<Gif>) -> Unit, onDismiss: () -> Unit) {
    val groups = groups.filter { it.size > 1 }
    if (groups.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.library_duplicates_action)) },
            text = { Text(stringResource(R.string.library_duplicates_none)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicates_close)) } }
        )
        return
    }
    // Only extras can be checked, so at least one file per group always survives.
    var checked by remember(groups) { mutableStateOf(groups.flatMap { it.drop(1) }.map { it.id }.toSet()) }
    var confirming by remember(groups) { mutableStateOf(false) }
    val toDelete = groups.flatMap { it.drop(1) }.filter { it.id in checked }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.library_duplicates_confirm_title)) },
            text = { Text(stringResource(R.string.library_duplicates_confirm_message, toDelete.size)) },
            confirmButton = {
                TextButton(onClick = { onDelete(toDelete) }) {
                    Text(stringResource(R.string.library_duplicates_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.library_duplicates_cancel)) }
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_duplicates_action)) },
        text = {
            Column {
                Text(stringResource(R.string.library_duplicates_summary, groups.size),
                    style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    groups.forEach { group ->
                        item(key = "keep-${group.first().id}") {
                            Text(stringResource(R.string.library_duplicates_keep, group.first().title),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        items(group.drop(1), key = { it.id }) { gif ->
                            val isChecked = gif.id in checked
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                    value = isChecked, role = Role.Checkbox,
                                    onValueChange = { checked = if (it) checked + gif.id else checked - gif.id })
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Text(gif.title, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }, enabled = toDelete.isNotEmpty()) {
                Text(stringResource(R.string.library_duplicates_delete, toDelete.size),
                    color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicates_close)) }
        }
    )
}
