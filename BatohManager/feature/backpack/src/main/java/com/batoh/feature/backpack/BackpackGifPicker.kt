package com.batoh.feature.backpack

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
import androidx.compose.material3.Card
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
                gifs.message ?: stringResource(R.string.backpack_picker_load_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            is Result.Success -> if (gifs.data.isEmpty()) {
                Text(
                    stringResource(R.string.backpack_picker_empty),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(gifs.data, key = { it.id }) { gif ->
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
