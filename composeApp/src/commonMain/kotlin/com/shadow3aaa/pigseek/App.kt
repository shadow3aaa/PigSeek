package com.shadow3aaa.pigseek

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    viewModel: PiggyViewModel = viewModel()
) {
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

                    Text(
                        text = "PigSeek", style = MaterialTheme.typography.titleLarge
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

                    val imageDatas by viewModel.images.collectAsState()
                    val filteredImageDatas = if (searchText.isBlank()) {
                        imageDatas
                    } else {
                        imageDatas.filter {
                            it.value.contains(searchText)
                        }
                    }

                    Gallery(
                        items = mapImageDataFilter(
                            filteredImageDatas
                        ), onDelete = { uri ->
                            viewModel.removePiggy(
                                uri
                            )
                        })
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).width(150.dp),
                ) {
                    ExtendedFloatingActionButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        showImagePicker = true
                    }, icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add, contentDescription = "添加新的小猪"
                        )
                    }, text = {
                        Text("添加新的小猪")
                    })

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    ExtendedFloatingActionButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        showExportPiggyPackDialog = true
                        viewModel.exportPiggyPack()
                    }, icon = {
                        Icon(
                            imageVector = Icons.Outlined.Share, contentDescription = "导出小猪包"
                        )
                    }, text = {
                        Text("导出小猪包")
                    })


                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    ExtendedFloatingActionButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        showPiggyPackPicker = true
                    }, icon = {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder, contentDescription = "导入小猪包"
                        )
                    }, text = {
                        Text("导入小猪包")
                    })
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