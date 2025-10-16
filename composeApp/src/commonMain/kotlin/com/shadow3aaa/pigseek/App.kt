package com.shadow3aaa.pigseek

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    viewModel: PiggyViewModel = viewModel()
) {
    var isFabMenuOpen by rememberSaveable { mutableStateOf(false) }
    viewModel.initPiggy()
    var showExportPiggyPackDialog by rememberSaveable { mutableStateOf(false) }

    var showPiggyPackPicker by rememberSaveable {
        mutableStateOf(false)
    }
    var pickedPiggyPackUri by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    PiggyPackPicker(
        show = showPiggyPackPicker, onPiggyPackPicked = {
            showPiggyPackPicker = false
            pickedPiggyPackUri = it
            it?.let {
                viewModel.importPiggyPack(it)
            }
        })

    var pickedImageData by rememberSaveable {
        mutableStateOf<ImageData?>(null)
    }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    ImagePicker(
        show = showImagePicker, onImagePicked = { uri ->
            showImagePicker = false
            uri?.let {
                pickedImageData = ImageData(
                    uri = uri, description = ""
                )
            }
        })

    MaterialTheme {
        Background {
            var searchText by rememberSaveable {
                mutableStateOf("")
            }

            Box(
                modifier = Modifier.fillMaxWidth().systemBarsPadding().imePadding().padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                )
            ) {
                Column {
                    Spacer(
                        modifier = Modifier.height(30.dp)
                    )

                    val syncState by viewModel.syncState.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PigSeek", style = MaterialTheme.typography.titleLarge
                        )

                        Card(shape = RoundedCornerShape(12.dp), onClick = {
                            viewModel.syncFromGitHub()
                        }) {
                            val importProgress by viewModel.importProgress.collectAsState()

                            val isImportComplete = importProgress >= 1f

                            val (text, progress, isProgressVisible) = when (val state = syncState) {
                                is SyncState.Idle -> Triple("同步云小猪", 0f, false)
                                is SyncState.FetchingMetadata -> Triple("正在获取云端小猪...", 0f, false)
                                is SyncState.Downloading -> Triple("正在更新数据...", state.progress, true)
                                is SyncState.Success -> Triple(state.message, 0f, false)
                                is SyncState.Error -> Triple(state.message, 0f, false)
                                is SyncState.Completed -> Triple("同步完成", 1f, true)
                            }

                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).wrapContentWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = text, style = MaterialTheme.typography.labelSmall
                                    )

                                    if (isImportComplete) {
                                        viewModel.resetSyncState()
                                    }

                                    AnimatedVisibility(isProgressVisible) {
                                        CircularProgressIndicator(
                                            progress = { progress }, modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    AnimatedVisibility(!isProgressVisible) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "云端同步"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val imageDatas by viewModel.images.collectAsState()

                    Text(
                        text = "已入住 ${imageDatas.size} 只小猪",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(50.dp))

                    OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = searchText, onValueChange = {
                        searchText = it
                    }, leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search, contentDescription = "搜索小猪..."
                        )
                    }, shape = RoundedCornerShape(percent = 50), placeholder = {
                        Text("搜索小猪...")
                    })

                    Spacer(modifier = Modifier.height(10.dp))


                    val filteredAndSortedImageDatas = remember(searchText, imageDatas) {
                        if (searchText.isBlank()) {
                            mapImageDataFilter(imageDatas) // 搜索框为空时，显示所有
                        } else {
                            val searchQuery = searchText.lowercase()

                            // 低于这个值的结果将被忽略
                            val SIMILARITY_THRESHOLD = 0.7

                            imageDatas.map { (sha, description) ->
                                val imageData = ImageData(uri = buildPiggyUri(sha = sha), description = description)
                                val descriptionLower = description.lowercase()

                                // 计算两个分数：
                                // 1. 是否直接包含（权重最高）
                                // 2. Jaro-Winkler 相似度分数
                                val containsMatch = descriptionLower.contains(searchQuery)
                                val similarity = jaroWinklerSimilarity(descriptionLower, searchQuery)

                                // 使用 Triple 存储图像数据、是否包含以及相似度分数
                                Triple(imageData, containsMatch, similarity)
                            }.filter { (_, containsMatch, similarity) ->
                                containsMatch || similarity >= SIMILARITY_THRESHOLD
                            }.sortedWith(
                                // 自定义排序规则：
                                // 1. “直接包含”的结果排在最前面
                                // 2. 如果两个都“直接包含”或都“不包含”，则按“相似度分数”降序排
                                compareByDescending<Triple<ImageData, Boolean, Double>> { it.second } // true (包含) > false
                                    .thenByDescending { it.third } // 按相似度降序
                            ).map { it.first } // 最后，只取出图像数据
                        }
                    }

                    Gallery(
                        items = filteredAndSortedImageDatas, // 使用新的、排序更优的列表
                        onDelete = { uri ->
                            viewModel.removePiggy(
                                uri
                            )
                        })
                }

                // FAB Menu
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = isFabMenuOpen,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically(),
                        modifier = Modifier.clipToBounds()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End
                        ) {
                            // Action: Import
                            ExtendedFloatingActionButton(onClick = {
                                showPiggyPackPicker = true
                                isFabMenuOpen = false
                            }, text = {
                                Text("导入小猪包")
                            }, icon = {
                                Icon(Icons.Default.CreateNewFolder, "导入小猪包")
                            })
                            // Action: Export
                            ExtendedFloatingActionButton(onClick = {
                                showExportPiggyPackDialog = true
                                viewModel.exportPiggyPack()
                                isFabMenuOpen = false
                            }, text = {
                                Text("导出小猪包")
                            }, icon = {
                                Icon(Icons.Default.Share, "导出小猪包")
                            })
                            // Action: Add
                            ExtendedFloatingActionButton(onClick = {
                                showImagePicker = true
                                isFabMenuOpen = false
                            }, text = {
                                Text("添加新的小猪")
                            }, icon = {
                                Icon(Icons.Default.Add, "添加新的小猪")
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FloatingActionButton(onClick = { isFabMenuOpen = !isFabMenuOpen }) {
                        Icon(
                            imageVector = if (isFabMenuOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            }

            if (pickedImageData != null) {
                Dialog(
                    onDismissRequest = { pickedImageData = null },
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PiggyCard(
                                modifier = Modifier.heightIn(max = 500.dp).align(
                                    Alignment.CenterHorizontally
                                ), imageData = pickedImageData!!
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            PiggyBuilderPanel(saveImageData = { description ->
                                pickedImageData!!.description = description
                                viewModel.addImage(pickedImageData!!)
                            }, closeDialog = {
                                pickedImageData = null
                            })
                        }
                    }
                }
            }

            if (showExportPiggyPackDialog) {
                Dialog(
                    onDismissRequest = {
                        showExportPiggyPackDialog = false
                    }) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            val progress by viewModel.exportProgress.collectAsState()
                            if (progress < 1.0) {
                                Text("导出小猪包中...")
                            } else {
                                Text("导出完成")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = {
                                    progress
                                })

                            Spacer(modifier = Modifier.height(10.dp))

                            AnimatedVisibility(progress < 1f) {
                                Text("${progress * 100f}%")
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                TextButton(
                                    enabled = progress >= 1f, onClick = {
                                        sharePiggyPack()
                                    }) {
                                    Text("分享")
                                }
                                TextButton(
                                    enabled = progress >= 1.0, onClick = {
                                        showExportPiggyPackDialog = false
                                    }) {
                                    Text("确认")
                                }
                            }
                        }
                    }
                }
            }

            var importFromShareUri by viewModel.importFromShare

            if (pickedPiggyPackUri != null || importFromShareUri != null) {
                Dialog(
                    onDismissRequest = {
                        pickedPiggyPackUri = null
                        importFromShareUri = null
                    }) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            val progress by viewModel.importProgress.collectAsState()
                            if (progress < 1.0) {
                                Text("导入小猪包中...")
                            } else {
                                Text("导入完成")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = {
                                    progress
                                })

                            Spacer(modifier = Modifier.height(10.dp))

                            AnimatedVisibility(progress < 1f) {
                                Text("${progress * 100f}%")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                modifier = Modifier.align(Alignment.End), enabled = progress >= 1.0, onClick = {
                                    pickedPiggyPackUri = null
                                    importFromShareUri = null
                                }) {
                                Text("确认")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PiggyBuilderPanel(
    modifier: Modifier = Modifier, saveImageData: (String) -> Unit, closeDialog: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        var description by rememberSaveable {
            mutableStateOf("")
        }

        OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = description, onValueChange = {
            description = it
        }, placeholder = {
            Text(
                "描述小猪...",
            )
        })

        Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.align(Alignment.End)
        ) {
            TextButton(
                onClick = {
                    closeDialog()
                }) {
                Text("取消")
            }

            Spacer(modifier = Modifier.width(5.dp))

            TextButton(
                enabled = description.isNotBlank(), onClick = {
                    saveImageData(
                        description
                    )
                    closeDialog()
                }) {
                Text("确认")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(description.isBlank()) {
            Text(
                modifier = Modifier.align(Alignment.End),
                text = "你还没有描述小猪",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

fun mapImageDataFilter(
    datas: Map<String, String>
): List<ImageData> {
    return datas.map { (sha, description) ->
        ImageData(
            uri = buildPiggyUri(sha = sha), description = description
        )
    }
}

expect fun buildPiggyUri(sha: String): String
expect fun sharePiggyPack()

@Composable
@Preview
fun Background(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}