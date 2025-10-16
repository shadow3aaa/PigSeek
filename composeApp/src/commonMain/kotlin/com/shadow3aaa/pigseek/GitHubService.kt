package com.shadow3aaa.pigseek

import com.oldguy.common.io.File
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.contentLength
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.let

object GitHubService {
    private const val GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun getLatestMetadataContent(owner: String, repo: String, branch: String = "main"): Result<String> {
        return try {
            val url = "$GITHUB_RAW_BASE_URL/$owner/$repo/$branch/metadata.json"
            val content = client.get(url).body<String>()
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRawFileUrl(owner: String, repo: String, assetName: String, branch: String = "main"): Result<String> {
        return try {
            val url = "$GITHUB_RAW_BASE_URL/$owner/$repo/$branch/output/$assetName"
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            client.prepareGet(url).execute { response ->
                val channel: ByteReadChannel = response.bodyAsChannel()
                val contentLength = response.contentLength()?.toFloat() ?: -1f
                var bytesRead = 0f

                destination.path.toPath().parent?.let { FileSystem.SYSTEM.createDirectories(it) }

                FileSystem.SYSTEM.write(destination.path.toPath()) {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (!channel.isClosedForRead) {
                        val bytesReadInLoop = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesReadInLoop <= 0) break
                        write(buffer, 0, bytesReadInLoop)
                        bytesRead += bytesReadInLoop
                        if (contentLength > 0) {
                            val progress = (bytesRead / contentLength).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                }
            }
            onProgress(1f) // Ensure completion
            Result.success(Unit)
        } catch (e: Exception) {
            onProgress(0f) // Reset on failure
            Result.failure(e)
        }
    }
}
