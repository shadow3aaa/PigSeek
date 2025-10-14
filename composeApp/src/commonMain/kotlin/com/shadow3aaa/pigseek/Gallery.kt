package com.shadow3aaa.pigseek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.launch

@Composable
fun Gallery(items: List<ImageData>) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
    ) {
        items(items = items) { img ->
            PiggyCard(
                imageData = img,
                onClick = {
                    when (sharePiggyImage(
                        uri = img.uri,
                        description = img.description
                    )) {
                        ShareType.Copy -> {
                            scope.launch {
                                snackbarHostState.showSnackbar("copied")
                            }
                        }
                        ShareType.Others -> {}
                    }
                }
            )
        }
    }
}

enum class ShareType {
    Copy,
    Others
}

expect fun sharePiggyImage(
    uri: String,
    description: String
): ShareType

@Composable
fun PiggyCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    imageData: ImageData,
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageData.uri)
                    .listener(
                        onError = { _, result ->
                            result.throwable.printStackTrace()
                        }
                    )
                    .build(),
                contentDescription = imageData.description,
                error = ColorPainter(Color.Red),
            )

            if (imageData.description.isNotBlank()) {
                Surface(
                    modifier = Modifier.padding(10.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        imageData.description
                    )
                }
            }
        }
    }
}

