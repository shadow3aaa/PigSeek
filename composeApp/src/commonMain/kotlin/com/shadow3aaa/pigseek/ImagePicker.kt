package com.shadow3aaa.pigseek

import androidx.compose.runtime.Composable

@Composable
expect fun ImagePicker(
    show: Boolean,
    onImagePicked: (String?) -> Unit // 返回选定文件的 URI
)
