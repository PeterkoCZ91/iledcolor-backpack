package com.batoh.feature.backpack

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackpackGifPicker(
    gifs: Result<List<Gif>>,
    onDismiss: () -> Unit,
    onSelect: (Gif) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        var query by remember { mutableStateOf("") }
        Text(
            stringResource(R.string.backpack_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            stringResource(R.string.backpack_picker_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
        when (gifs) {
            Result.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is Result.Error -> Text(
                stringResource(R.string.backpack_picker_load_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            is Result.Success -> if (gifs.data.isEmpty()) {
                PickerMessage(
                    text = stringResource(R.string.backpack_picker_empty),
                    actionLabel = stringResource(R.string.gif_picker_close_action),
                    onAction = onDismiss
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.gif_picker_search_hint)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.gif_picker_search_clear))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                val shown = remember(gifs.data, query) { GifPickerLogic.filter(gifs.data, query) }
                if (shown.isEmpty()) {
                    PickerMessage(
                        text = stringResource(R.string.gif_picker_no_match),
                        actionLabel = stringResource(R.string.gif_picker_clear_search_action),
                        onAction = { query = "" }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(128.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.id }) { gif ->
                            Card(onClick = { onSelect(gif) }) {
                                Column {
                                    AsyncImage(
                                        model = gif.thumbnailUrl,
                                        contentDescription = gif.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    )
                                    Text(
                                        gif.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                    if (GifPickerLogic.isNotSquare(gif.width, gif.height)) {
                                        Text(
                                            stringResource(R.string.gif_picker_not_square_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Keep the grid's last row above the system navigation bar (the sheet is drawn edge to edge).
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun PickerMessage(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
    }
}
