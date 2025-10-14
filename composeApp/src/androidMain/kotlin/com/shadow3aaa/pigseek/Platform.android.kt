package com.shadow3aaa.pigseek

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.oldguy.common.io.IOException
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.net.URI

lateinit var appContext: Context

@Composable
actual fun ImagePicker(
    show: Boolean,
    onImagePicked: (String?) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            onImagePicked(uri?.toString())
        }
    )

    LaunchedEffect(show) {
        if (show) {
            launcher.launch("image/*")
        }
    }
}

@Composable
actual fun PiggyPackPicker(show: Boolean, onPiggyPackPicked: (String?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            println(uri)
            onPiggyPackPicked(uri?.toString())
        }
    )

    LaunchedEffect(show) {
        if (show) {
            launcher.launch("application/zip")
        }
    }
}

actual fun getPiggyPackFileFromUri(uri: String): com.oldguy.common.io.File {
    val contentResolver = appContext.contentResolver
    val inputStream = contentResolver.openInputStream(uri.toUri())
        ?: throw IllegalArgumentException("Cannot open URI: $uri")

    val tempFile = File(getCachePath(), "temp_piggy_pack.zip")
    if (tempFile.exists()) {
        tempFile.delete()
    }

    tempFile.sink().buffer().use { sink ->
        inputStream.source().buffer().use { source ->
            sink.writeAll(source)
        }
    }
    inputStream.close()

    return com.oldguy.common.io.File(
        filePath = tempFile.absolutePath
    )
}

actual fun readImage(uri: String): ByteArray {
    val androidUri = uri.toUri()
    val inputStream = appContext.contentResolver.openInputStream(androidUri)
        ?: throw IllegalArgumentException("Cannot open URI: $uri")

    inputStream.source().buffer().use { source ->
        return source.readByteArray()
    }
}

actual fun saveIntoPiggyHome(
    sha: String,
    rawImageData: ByteArray
) {
    val internalFilesDir = appContext.filesDir
    val piggyHomeDir = File(internalFilesDir, "piggy_home")

    if (!piggyHomeDir.exists()) {
        piggyHomeDir.mkdirs()
    }

    val targetFile = File(piggyHomeDir, sha)

    targetFile.sink().buffer().use { sink ->
        sink.write(rawImageData)
    }

    println("Image saved to: ${targetFile.absolutePath}")
}

actual fun buildPiggyUri(sha: String): String {
    val internalFilesDir = appContext.filesDir
    val piggyHomeDir = File(internalFilesDir, "piggy_home")
    val targetFile = File(piggyHomeDir, sha)

    return Uri.fromFile(targetFile).toString()
}

actual fun deletePiggyFileFromUri(uri: String): String {
    val parsed = Uri.parse(uri)
    val path = parsed.path ?: throw IllegalArgumentException("Invalid uri: $uri")
    val file = File(path)

    if (!file.exists()) {
        throw IOException("File not found: $path")
    }
    if (!file.delete()) {
        throw IOException("Failed to delete file: $path")
    }

    // 删除成功，返回 sha（文件名）
    return file.name
}

private fun getMetadataFile(context: Context): File {
    val internalFilesDir = context.filesDir
    val piggyHomeDir = File(internalFilesDir, "piggy_home")
    return File(piggyHomeDir, "metadata.json")
}

actual fun readMetadataRaw(): String {
    val metadataFile = getMetadataFile(appContext)
    if (!metadataFile.exists()) {
        return ""
    }

    return metadataFile.source().buffer().use { source ->
        source.readUtf8()
    }
}

actual fun saveMetadataRaw(raw: String) {
    val metadataFile = getMetadataFile(appContext)
    metadataFile.parentFile!!.mkdirs()
    metadataFile.sink().buffer().use { sink ->
        sink.writeUtf8(raw)
    }
}

actual fun piggyHomePath(): String {
    val internalFilesDir = appContext.filesDir
    val piggyHomeDir = File(internalFilesDir, "piggy_home")
    return piggyHomeDir.toPath().toString()
}

actual fun getCachePath(): String {
    // context.cacheDir provides an internal directory for your app's temporary cache files.
    // The system may delete these files when storage is low.
    val cacheDir: File = appContext.cacheDir
    return cacheDir.absolutePath
}

fun File.toUriCompat(context: Context): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", this)
}

actual fun sharePiggyPack() {
    val file = getCachePath().toPath().resolve("PiggyPackage.zip").toFile()
    val fileUri = FileProvider.getUriForFile(
        appContext,
        "com.shadow3aaa.pigseek.provider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享小猪包")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(
        chooser
    )
}

fun detectImageMimeType(file: File): String? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outMimeType // 例如 "image/gif"、"image/jpeg"
    } catch (e: Exception) {
        null
    }
}

actual fun sharePiggyImage(uri: String, description: String): ShareType {
    try {
        val cacheDir = getCachePath().toPath().toFile()
        cacheDir.listFiles { f -> f.name.startsWith("share_") }?.forEach { it.delete() }
    } catch (e: Exception) { }

    try {
        val sourceFile = File(URI(uri))
        val mime = detectImageMimeType(sourceFile)
        val ext = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mime)
            ?: "jpg"
        val destFile = File(getCachePath(), "share_${description}.${ext}")

        sourceFile.copyTo(destFile, overwrite = true)

        val imageUri = destFile.toUriCompat(appContext)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            clipData = ClipData.newUri(appContext.contentResolver, "image", imageUri)
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TITLE, "分享 $description")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val shareIntent = Intent.createChooser(sendIntent, "分享小猪图片")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(shareIntent)

    } catch (e: Exception) {
        e.printStackTrace()
    }

    return ShareType.Others
}

actual fun Modifier.contextClick(
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onContextClick: () -> Unit
): Modifier = this.then(
    Modifier.combinedClickable(
        onClick = onClick,
        onDoubleClick = onDoubleClick,
        onLongClick = onContextClick
    )
)
