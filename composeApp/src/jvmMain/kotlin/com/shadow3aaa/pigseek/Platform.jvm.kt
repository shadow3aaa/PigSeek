package com.shadow3aaa.pigseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.oldguy.common.io.Uri
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import okio.source
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

@Composable
actual fun ImagePicker(
    show: Boolean, onImagePicked: (String?) -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            val chooser = JFileChooser()
            chooser.dialogTitle = "选择图片"
            chooser.fileFilter = FileNameExtensionFilter("图片文件 (*.jpg, *.png)", "jpg", "jpeg", "png")

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onImagePicked(chooser.selectedFile.toURI().toString().replace("file:/", "file://"))
            } else {
                onImagePicked(null)
            }
        }
    }
}

@Composable
actual fun PiggyPackPicker(show: Boolean, onPiggyPackPicked: (String?) -> Unit) {
    LaunchedEffect(show) {
        if (show) {
            val chooser = JFileChooser()
            chooser.dialogTitle = "选择猪包"
            chooser.fileFilter = FileNameExtensionFilter("猪包文件 (*.zip)", "zip")

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onPiggyPackPicked(
                    chooser.selectedFile.toString()
                )
            } else {
                onPiggyPackPicked(null)
            }
        }
    }
}

actual fun getPiggyPackFileFromUri(uri: String): com.oldguy.common.io.File {
    return com.oldguy.common.io.File(
        filePath = uri
    )
}

actual fun readImage(uri: String): ByteArray {
    val path = if (uri.startsWith("file://")) {
        uri.removePrefix("file://").toPath()
    } else {
        uri.toPath()
    }
    return FileSystem.SYSTEM.read(path) {
        readByteArray()
    }
}

actual fun saveIntoPiggyHome(
    sha: String, rawImageData: ByteArray
) {
    val userHome = System.getProperty("user.home")
    val piggyHomeDir = File(userHome, ".piggy_home")

    if (!piggyHomeDir.exists()) {
        piggyHomeDir.mkdirs()
    }

    val targetFile = File(piggyHomeDir, sha)

    targetFile.sink().buffer().use { sink ->
        sink.write(rawImageData)
    }
}

actual fun buildPiggyUri(sha: String): String {
    val userHome = System.getProperty("user.home")
    val piggyHomeDir = File(userHome, ".piggy_home")
    val targetFile = File(piggyHomeDir, sha)
    return targetFile.toURI().toString().replace("file:/", "file://")
}

private fun getMetadataFile(): File {
    val userHome = System.getProperty("user.home")
    val piggyHomeDir = File(userHome, ".piggy_home")
    return File(piggyHomeDir, "metadata.json")
}

actual fun readMetadataRaw(): String {
    val metadataFile = getMetadataFile()
    if (!metadataFile.exists()) {
        return ""
    }

    return metadataFile.source().buffer().use { source ->
        source.readUtf8()
    }
}

actual fun saveMetadataRaw(raw: String) {
    val metadataFile = getMetadataFile()

    // 确保父目录 (.piggy_home) 存在
    metadataFile.parentFile.mkdirs()

    metadataFile.sink().buffer().use { sink ->
        sink.writeUtf8(raw)
    }
}

actual fun piggyHomePath(): String {
    val userHome = System.getProperty("user.home")
    val piggyHomeDir = File(userHome, ".piggy_home")
    return piggyHomeDir.toPath().toString()
}

actual fun getCachePath(): String {
    // "java.io.tmpdir" is a standard system property that provides
    // the path to the OS-designated temporary directory (e.g., /tmp on Linux,
    // or C:\Users\Username\AppData\Local\Temp on Windows).
    return System.getProperty("java.io.tmpdir")
}

actual fun sharePiggyPack() {
    val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
    val file = getCachePath().toPath().resolve("PiggyPackage.zip").toFile()
    fileChooser.dialogTitle = "保存小猪包到指定目录"
    fileChooser.selectedFile = file
    val result = fileChooser.showSaveDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val targetFile = fileChooser.selectedFile
        file.copyTo(targetFile)
    }
}

private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.imageFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        return DataFlavor.imageFlavor.equals(flavor)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (isDataFlavorSupported(flavor)) {
            return image
        }
        throw UnsupportedFlavorException(flavor)
    }
}

actual fun sharePiggyImage(uri: String, description: String): ShareType {
    try {
        val imageFile = File(java.net.URI(uri))

        val image = ImageIO.read(imageFile)

        if (image != null) {
            val transferable = ImageTransferable(image)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(transferable, null)
            println("Image copied to clipboard.")
        } else {
            println("Failed to read image from file.")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 即使复制图片成功，也返回 Copy 类型
    return ShareType.Copy
}