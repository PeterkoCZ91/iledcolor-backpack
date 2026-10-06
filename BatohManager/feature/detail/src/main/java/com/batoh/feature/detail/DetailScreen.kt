package com.batoh.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onSendToBackpack: (String) -> Unit,
    onEditGif: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScreen(
        gifUrl = viewModel.gifUrl,
        mp4Url = viewModel.mp4Url,
        isLocal = viewModel.isLocal,
        uiState = uiState,
        onSaveClick = viewModel::onSaveClick,
        onConvertForBackpackClick = viewModel::onConvertForBackpackClick,
        onSendToBackpack = { onSendToBackpack(viewModel.gifUrl) },
        onEditGif = { onEditGif(viewModel.gifUrl) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    gifUrl: String,
    mp4Url: String,
    isLocal: Boolean,
    uiState: DetailUiState,
    onSaveClick: () -> Unit,
    onConvertForBackpackClick: () -> Unit,
    onSendToBackpack: () -> Unit,
    onEditGif: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var previewError by remember(gifUrl) { mutableStateOf(false) }
    var previewLoaded by remember(gifUrl) { mutableStateOf(false) }
    // Bumped by the "reload preview" recovery action to recreate the image request.
    var previewAttempt by remember(gifUrl) { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                }
            )
        },
        bottomBar = {
            DetailActionBar(
                isLocal = isLocal,
                uiState = uiState,
                mp4Url = mp4Url,
                onShareLocal = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(gifUrl))
                        type = "image/gif"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                },
                onSaveClick = onSaveClick,
                onConvertForBackpackClick = onConvertForBackpackClick,
                onSendToBackpack = onSendToBackpack,
                onEditGif = onEditGif,
                previewLoaded = previewLoaded
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // GIF Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                key(previewAttempt) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(gifUrl)
                        .build(),
                    contentDescription = when {
                        previewError -> stringResource(R.string.detail_preview_error)
                        previewLoaded -> stringResource(R.string.detail_preview_loaded)
                        else -> stringResource(R.string.detail_preview_description)
                    },
                    onError = {
                        previewError = true
                        previewLoaded = false
                        Log.w("GifPreview", "Detail decode failed", it.result.throwable)
                    },
                    onSuccess = { previewError = false; previewLoaded = true },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                }

                if (previewError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.detail_preview_failed),
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        TextButton(onClick = {
                            previewError = false
                            previewAttempt++
                        }) { Text(stringResource(R.string.detail_retry_preview), color = Color.White) }
                    }
                }

                // 64×64 minipreview — pixel-exact first frame as the backpack shows it
                if (mp4Url.isNotBlank() || (isLocal && previewLoaded)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(96.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = remember(gifUrl) {
                                ImageRequest.Builder(context)
                                    .data(gifUrl)
                                    .decoderFactory(coil.decode.BitmapFactoryDecoder.Factory())
                                    .size(512)
                                    .allowHardware(false)
                                    .transformations(PanelPreviewTransformation())
                                    .build()
                            },
                            contentDescription = stringResource(R.string.detail_backpack_preview_description),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.None
                        )
                        Text(
                            text = "64×64",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 2.dp)
                                // Size is already in the mini preview's content description.
                                .clearAndSetSemantics { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailActionBar(
    isLocal: Boolean,
    uiState: DetailUiState,
    mp4Url: String,
    onShareLocal: () -> Unit,
    onSaveClick: () -> Unit,
    onConvertForBackpackClick: () -> Unit,
    onSendToBackpack: () -> Unit,
    onEditGif: () -> Unit,
    previewLoaded: Boolean
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isLocal) {
                Button(onClick = onSendToBackpack, enabled = previewLoaded,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detail_send_to_backpack)) }
                androidx.compose.material3.OutlinedButton(onClick = onEditGif, enabled = previewLoaded,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detail_edit_for_backpack)) }
                // No early return here: return@Column from an inline composable lambda
                // corrupts the composer's slot table (ArrayIndexOutOfBoundsException on
                // recomposition), so the non-local branch must be a plain if/else.
                Button(
                    onClick = onShareLocal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(stringResource(R.string.detail_share), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val controlsEnabled = uiState !is DetailUiState.Saving && uiState !is DetailUiState.Converting
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSaveClick,
                        // Polite live region: TalkBack announces Saving → Saved.
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState is DetailUiState.Saved) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.primary
                        ),
                        enabled = controlsEnabled
                    ) {
                        when (uiState) {
                            is DetailUiState.Saving -> {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(stringResource(R.string.detail_saving), style = MaterialTheme.typography.labelLarge)
                            }
                            is DetailUiState.Saved -> {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(stringResource(R.string.detail_saved), style = MaterialTheme.typography.labelLarge)
                            }
                            else -> {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(stringResource(R.string.detail_save_button), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    if (mp4Url.isNotBlank()) {
                        Button(
                            onClick = onConvertForBackpackClick,
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = controlsEnabled && uiState !is DetailUiState.ConvertedAndSaved,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState is DetailUiState.ConvertedAndSaved) Color(0xFF4CAF50)
                                else Color(0xFF7B1FA2)
                            )
                        ) {
                            when (uiState) {
                                is DetailUiState.Converting -> {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(
                                        stringResource(R.string.detail_converting, uiState.progress),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                                is DetailUiState.ConvertedAndSaved -> {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(stringResource(R.string.detail_converted), style = MaterialTheme.typography.labelLarge, color = Color.White)
                                }
                                else -> {
                                    Text(stringResource(R.string.detail_convert_backpack), style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
            }

            if (uiState is DetailUiState.Error) {
                // The save/convert buttons stay enabled in Error, so they double as retry.
                Text(uiState.message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive })
            }
            if (uiState is DetailUiState.Converting) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = uiState.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }
        }
    }
}
