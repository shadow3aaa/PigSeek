package com.shadow3aaa.pigseek

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

@Composable
fun Gallery(items: List<ImageData>, onDelete: (String) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
        contentPadding = PaddingValues(
            bottom = 100.dp
        )
    ) {
        items(items = items) { img ->
            var showMenu by rememberSaveable { mutableStateOf(false) }

            Box {
                PiggyCard(
                    modifier = Modifier.contextClick(
                        onContextClick = {
                            showMenu = true  // 右键时显示菜单
                        },
                        onClick = {
                            println("clicked")
                            when (sharePiggyImage(
                                uri = img.uri,
                                description = img.description
                            )) {
                                ShareType.Copy -> {
                                    // TODO: show snackbar
                                }
                                ShareType.Others -> {}
                            }
                        }
                    ).fillMaxWidth(),
                    imageData = img,
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            onDelete(img.uri)
                            showMenu = false
                        }
                    )
                }
            }
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
    onClick: (() -> Unit)? = null,
    imageData: ImageData,
) {
    var finalModifier = modifier
    if (onClick != null) {
        finalModifier = modifier.clickable {
            onClick()
        }
    }

    Card(
        modifier = finalModifier,
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

