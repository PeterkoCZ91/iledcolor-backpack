package com.batoh.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.batoh.core.domain.model.Gif

/** Which copies the simple "delete duplicates" flow removes: everything except the first of each group. */
internal object DuplicatePlan {
    fun <T> extras(groups: List<List<T>>): List<T> = groups.filter { it.size > 1 }.flatMap { it.drop(1) }
}

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
    val extras = DuplicatePlan.extras(groups)
    if (extras.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.library_duplicates_action)) },
            text = { Text(stringResource(R.string.library_duplicates_none)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicates_close)) } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_duplicates_action)) },
        text = { Text(pluralStringResource(R.plurals.library_duplicates_simple_message, extras.size, extras.size)) },
        confirmButton = {
            TextButton(onClick = { onDelete(extras) }) {
                Text(stringResource(R.string.library_duplicates_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicates_cancel)) }
        }
    )
}
