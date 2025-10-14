package com.shadow3aaa.pigseek

import androidx.compose.runtime.Composable

@Composable
expect fun PiggyPackPicker(
    show: Boolean,
    onPiggyPackPicked: (String?) -> Unit
)
