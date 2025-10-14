package com.shadow3aaa.pigseek

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import pigseek.composeapp.generated.resources.Res
import pigseek.composeapp.generated.resources.icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "pigseek",
        icon = AppIcon()
    ) {
        App()
    }
}

@Composable
fun AppIcon(): Painter {
    return painterResource(Res.drawable.icon)
}
