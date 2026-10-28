package com.batoh.feature.backpack

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.batoh.core.data.bluetooth.PanelSpec

/** UI state of the "Where to send?" step that precedes a send which starts with a GIF. */
sealed interface SendChooser {
    object Hidden : SendChooser
    /** Looking for my panels in range; shown inline, no sheet yet. */
    data class Scanning(val uri: android.net.Uri) : SendChooser
    data class Choosing(val uri: android.net.Uri, val targets: List<SendTarget>, val offerAll: Boolean) : SendChooser
}

/** "Name · W × H · connected/in range" as one line; the size reads "unknown type" while the panel's size is unknown. */
@Composable
internal fun sendTargetDetails(target: SendTarget): String = listOf(
    target.spec?.let { stringResource(R.string.panel_size, it.width, it.height) } ?: stringResource(R.string.panel_unknown_type),
    stringResource(if (target.connected) R.string.send_target_connected else R.string.my_panels_in_range)
).joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendTargetSheet(
    state: SendChooser.Choosing,
    onPick: (SendTarget) -> Unit,
    onAll: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.send_target_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp))
            if (state.targets.isEmpty()) {
                Text(stringResource(R.string.send_target_none), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.send_target_retry))
                }
            }
            state.targets.forEach { target ->
                val name = target.name ?: stringResource(R.string.backpack_unknown_device)
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(sendTargetDetails(target)) },
                    modifier = Modifier.heightIn(min = 48.dp).clickable(
                        onClickLabel = stringResource(R.string.send_target_a11y_send_to, name), role = Role.Button,
                        onClick = { onPick(target) })
                )
            }
            if (state.offerAll) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.send_target_all)) },
                    supportingContent = { Text(stringResource(R.string.send_target_all_hint)) },
                    modifier = Modifier.heightIn(min = 48.dp).clickable(
                        onClickLabel = stringResource(R.string.send_target_a11y_send_all), role = Role.Button, onClick = onAll)
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

/** Inline "looking for my panels" row while [SendChooser.Scanning]. */
@Composable
internal fun SendScanningRow(onCancel: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.multi_send_scanning), modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
    }
}

/** Small "Send a GIF from…" sheet: the phone's own files or the app's collection. Both end in the "Where to send?" step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendSourceSheet(
    onPhone: () -> Unit,
    onCollection: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.send_source_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.send_source_phone)) },
                supportingContent = { Text(stringResource(R.string.send_source_phone_hint)) },
                modifier = Modifier.heightIn(min = 48.dp).clickable(
                    onClickLabel = stringResource(R.string.send_source_phone_a11y), role = Role.Button, onClick = onPhone)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.send_source_collection)) },
                supportingContent = { Text(stringResource(R.string.send_source_collection_hint)) },
                modifier = Modifier.heightIn(min = 48.dp).clickable(
                    onClickLabel = stringResource(R.string.send_source_collection_a11y), role = Role.Button, onClick = onCollection)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}
