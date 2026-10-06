package com.batoh.feature.convert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ConvertRoute(
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    viewModel: ConvertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()

    ConvertScreen(
        url = url,
        uiState = uiState,
        onUrlChange = viewModel::onUrlChange,
        onConvertClick = viewModel::onConvertClick,
        onReset = viewModel::onReset,
        onCancel = viewModel::cancelConversion,
        onBack = onBack,
        onNavigateToLibrary = onNavigateToLibrary
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConvertScreen(
    url: String,
    uiState: ConvertUiState,
    onUrlChange: (String) -> Unit,
    onConvertClick: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.convert_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.convert_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Read clipboard URL
            val clipboardManager = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipboardUrl = remember(url) {
                if (url.isBlank()) {
                    try {
                        val clip = clipboardManager.primaryClip
                        val text = clip?.getItemAt(0)?.text?.toString()
                        if (text != null && text.startsWith("http")) text else null
                    } catch (_: Exception) { null }
                } else null
            }

            when (uiState) {
                is ConvertUiState.Idle, is ConvertUiState.Error -> {
                    IdleContent(
                        url = url,
                        clipboardUrl = clipboardUrl,
                        error = (uiState as? ConvertUiState.Error)?.message,
                        onUrlChange = onUrlChange,
                        onConvertClick = {
                            keyboard?.hide()
                            onConvertClick()
                        }
                    )
                }

                is ConvertUiState.Downloading -> {
                    ProgressContent(
                        label = stringResource(R.string.convert_downloading, uiState.progress),
                        progress = uiState.progress / 100f
                    )
                    Button(onClick = onCancel) { Text(stringResource(R.string.convert_cancel)) }
                }

                is ConvertUiState.Converting -> {
                    ProgressContent(
                        label = stringResource(R.string.convert_converting, uiState.progress),
                        progress = uiState.progress / 100f
                    )
                    Button(onClick = onCancel) { Text(stringResource(R.string.convert_cancel)) }
                }

                is ConvertUiState.Cancelling -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.convert_cancelling))
                }

                is ConvertUiState.Preview -> {
                    PreviewContent(
                        gifUri = uiState.gifUri.toString(),
                        onReset = onReset,
                        onNavigateToLibrary = onNavigateToLibrary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleContent(
    url: String,
    clipboardUrl: String?,
    error: String?,
    onUrlChange: (String) -> Unit,
    onConvertClick: () -> Unit
) {
    Text(
        text = stringResource(R.string.convert_hint),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Info chip about target resolution
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.convert_resolution_info),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Clipboard URL suggestion
    if (clipboardUrl != null) {
        AssistChip(
            onClick = { onUrlChange(clipboardUrl) },
            label = { Text(stringResource(R.string.convert_paste_clipboard)) },
            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.convert_url_label)) },
        placeholder = { Text("https://example.com/video.mp4") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onConvertClick() }),
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onConvertClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        enabled = url.isNotBlank()
    ) {
        Text(
            text = stringResource(R.string.convert_button),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun ProgressContent(label: String, progress: Float) {
    Spacer(modifier = Modifier.height(48.dp))

    CircularProgressIndicator(modifier = Modifier.size(64.dp))

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    LinearProgressIndicator(
        progress = progress,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PreviewContent(
    gifUri: String,
    onReset: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    Text(
        text = stringResource(R.string.convert_saved),
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF4CAF50),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    // GIF preview — shown at actual backpack size + large scaled version
    Box(
        modifier = Modifier
            .size(256.dp)
            .background(Color.Black, RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(gifUri)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }

    Text(
        text = stringResource(R.string.convert_preview_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onNavigateToLibrary,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50)
        )
    ) {
        Text(
            text = stringResource(R.string.convert_go_to_library),
            style = MaterialTheme.typography.titleMedium
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onReset,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            text = stringResource(R.string.convert_another),
            style = MaterialTheme.typography.titleSmall
        )
    }
}
