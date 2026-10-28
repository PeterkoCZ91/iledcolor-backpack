package com.batoh.feature.backpack

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batoh.core.data.bluetooth.UploadFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What the failure message after a send offers; [chooseAnother] only when the panel could not be reached. */
data class SendFailurePrompt(val chooseAnother: Boolean)

/** Pure decision for the failure message shown away from the Panel screen; JVM-tested. */
object SendFailurePlan {
    /**
     * Null when no message is shown: not a failed single send, a user cancel, or a failure the Panel screen
     * already shows ([onPanelScreen]). "Choose another panel" needs the source GIF ([hasSource]) and a panel that
     * was not reachable, because that is the case another panel can fix. A "send to all" run reports per panel on
     * the Panel screen and is not covered.
     */
    fun prompt(stage: UploadStage, failure: UploadFailure?, hasSource: Boolean, isMulti: Boolean, onPanelScreen: Boolean): SendFailurePrompt? {
        if (stage != UploadStage.Error || failure == null || failure == UploadFailure.Cancelled) return null
        if (isMulti || onPanelScreen) return null
        val unreachable = failure == UploadFailure.NotConnected || failure == UploadFailure.ConnectionLost
        return SendFailurePrompt(chooseAnother = unreachable && hasSource)
    }
}

@HiltViewModel
internal class SendFailureViewModel @Inject constructor(private val transfers: BackpackTransferManager) : ViewModel() {
    val uploadState = transfers.uploadState
    /** Re-runs the last send through the same upload path (same target, same GIF). */
    fun retry() = transfers.retryUpload()
}

/**
 * Failure message for a send started on another screen (Detail, Search, Convert, a shared file): the Panel screen
 * shows the details, so this appears only elsewhere. Place it where a Snackbar fits; [onChooseAnother] receives the
 * GIF uri and must open the Panel screen with it, which re-opens the "Where to send?" step.
 */
@Composable
fun SendFailureSnackbar(onPanelScreen: Boolean, onChooseAnother: (String) -> Unit) {
    val viewModel: SendFailureViewModel = hiltViewModel()
    val state by viewModel.uploadState.collectAsStateWithLifecycle()
    val preflight = rememberSendPreflight()
    var dismissed by remember { mutableStateOf<UploadState?>(null) }
    val prompt = SendFailurePlan.prompt(state.stage, state.failure, state.uri != null, state.multi != null, onPanelScreen)
    LaunchedEffect(state.stage) { if (state.stage != UploadStage.Error) dismissed = null }
    // A failure already seen on the Panel screen is not repeated on the next screen.
    LaunchedEffect(state, onPanelScreen) { if (onPanelScreen && state.stage == UploadStage.Error) dismissed = state }
    if (prompt == null || dismissed == state) return
    val uri = state.uri
    Snackbar(
        action = {
            TextButton(onClick = { dismissed = state; preflight.request(viewModel::retry) }) {
                Text(stringResource(R.string.common_retry))
            }
        },
        dismissAction = if (prompt.chooseAnother && uri != null) {
            {
                TextButton(onClick = { dismissed = state; onChooseAnother(uri) }) {
                    Text(stringResource(R.string.send_failure_choose_another), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        } else null,
    ) {
        Text(state.message ?: stringResource(R.string.backpack_upload_failed))
    }
}
