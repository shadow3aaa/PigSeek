package com.shadow3aaa.pigseek

import androidx.compose.ui.Modifier

expect fun Modifier.contextClick(
    onClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onContextClick: () -> Unit = {} // 桌面=右键，移动=长按
): Modifier