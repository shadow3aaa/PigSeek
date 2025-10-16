package com.shadow3aaa.pigseek

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oldguy.common.io.File
import com.oldguy.common.io.FileMode
import com.oldguy.common.io.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.* 
import okio.Path.Companion.toPath

class PiggyViewModel : ViewModel() {
    private var inited = false
    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()
    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()
    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()
    var importFromShare = mutableStateOf<String?>(null)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun syncFromGitHub() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.FetchingMetadata
                val repoOwner = "shadow3aaa"
                val repoName = "PigSeek-Data"

                val metadataResult = GitHubService.getLatestMetadataContent(repoOwner, repoName)

                metadataResult.onSuccess { metadataJson ->
                    val remoteImages = Json.decodeFromString<Map<String, String>>(metadataJson)
                    val localImages = _images.value

                    val missingShas = remoteImages.keys - localImages.keys

                    if (missingShas.isEmpty()) {
                        delay(100)
                        _syncState.value = SyncState.Success("云端小猪已全部入住")
                        delay(1000)
                        _syncState.value = SyncState.Idle
                        return@launch
                    }

                    var downloadedCount = 0
                    val totalToDownload = missingShas.size

                    for (sha in missingShas) {
                        _syncState.value = SyncState.Downloading(downloadedCount.toFloat() / totalToDownload.toFloat())
                        val urlResult = GitHubService.getRawFileUrl(repoOwner, repoName, sha)
                        urlResult.onSuccess { url ->
                            val destination = File(piggyHomePath().toPath().resolve(sha).toString())
                            val downloadResult = GitHubService.downloadFile(url, destination) { _ ->
                                // Individual file progress can be handled here if needed
                            }

                            if (downloadResult.isSuccess) {
                                downloadedCount++
                                _syncState.value = SyncState.Downloading(downloadedCount.toFloat() / totalToDownload.toFloat())
                            } else {
                                throw downloadResult.exceptionOrNull() ?: Exception("Unknown download error")
                            }
                        }.onFailure {
                            throw it
                        }
                    }

                    _images.value = remoteImages
                    saveMetadataRaw(metadataJson)
                    _syncState.value = SyncState.Completed
                    delay(100)
                    _syncState.value = SyncState.Idle
                }.onFailure {
                    _syncState.value = SyncState.Error("获取元数据失败: ${it.message}")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("同步出错: ${e.message}")
            }
        }
    }

    fun initPiggy() {
        if (inited) {
            return
        }

        inited = true
        viewModelScope.launch {
            val metadataRaw = readMetadataRaw()
            try {
                _images.value = Json.decodeFromString(metadataRaw)
            } catch (_: Exception) {
                saveMetadataRaw("{}")
            }
        }
    }

    fun addImage(
        imageData: ImageData
    ) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val rawImageData = readImage(
                imageData.uri
            )
            val sha = calculateImageSHA256(rawImageData)
            saveIntoPiggyHome(
                sha = sha, rawImageData = rawImageData
            )
            _images.value += (sha to imageData.description)
            val newMetadataRaw = Json.encodeToString(
                _images.value
            )
            saveMetadataRaw(
                newMetadataRaw
            )
        }
    }

    fun exportPiggyPack() {
        viewModelScope.launch(Dispatchers.IO) {
            _exportProgress.value = 0f
            val len = _images.value.keys.size + 1 // ADD 1是为了metadata.json
            val zip = File(filePath = getCachePath().toPath().resolve("PiggyPackage.zip").toString())
            val sourceDirectory = File(piggyHomePath())
            if (zip.exists) {
                zip.delete()
            }
            var counter = 0

            ZipFile(fileArg = zip, mode = FileMode.Write, zip64 = false).use { zip ->
                zip.zipDirectory(sourceDirectory, shallow = false) { name ->
                    counter += 1
                    _exportProgress.value = counter.toFloat() / len.toFloat()
                    true
                }
            }
        }
    }

    fun importPiggyPack(sourceUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _importProgress.value = 0f

            // 1. 读取现有元数据
            val existingMetadata = try {
                Json.decodeFromString<Map<String, String>>(readMetadataRaw()).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

            val zipFile = getPiggyPackFileFromUri(sourceUri)
            val targetDir = File(piggyHomePath()) // 解压到 piggyHomePath

            // 2. 解压
            ZipFile(zipFile, FileMode.Read).use { zip ->
                val entries = zip.entries
                val total = entries.size
                var counter = 0

                zip.extractToDirectory(
                    directory = targetDir,
                    filter = null,
                ) { entry ->
                    counter += 1
                    _importProgress.value = counter.toFloat() / total.toFloat()
                    entry.name
                }
            }

            val newMetadataRaw = readMetadataRaw()
            val newMetadata = Json.decodeFromString<Map<String, String>>(newMetadataRaw)
            existingMetadata.putAll(newMetadata)

            _images.value = existingMetadata
            saveMetadataRaw(Json.encodeToString(existingMetadata))
        }
    }

    fun removePiggy(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sha = deletePiggyFileFromUri(uri)
            val currentImages = _images.value.toMutableMap()
            currentImages.remove(sha)
            _images.value = currentImages
            val newMetadataRaw = Json.encodeToString(
                _images.value
            )
            saveMetadataRaw(
                newMetadataRaw
            )
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
        _importProgress.value = 0f
    }
}

fun calculateImageSHA256(byteArray: ByteArray): String {
    val sha256Sink = HashingSink.sha256(blackholeSink())

    sha256Sink.buffer().use { bufferedSink ->
        bufferedSink.write(byteArray)
    }

    val hash: ByteString = sha256Sink.hash
    return hash.hex()
}

expect fun readImage(uri: String): ByteArray
expect fun saveIntoPiggyHome(
    sha: String, rawImageData: ByteArray
)

expect fun readMetadataRaw(): String
expect fun saveMetadataRaw(raw: String)
expect fun piggyHomePath(): String
expect fun getCachePath(): String
expect fun getPiggyPackFileFromUri(uri: String): File
expect fun deletePiggyFileFromUri(uri: String): String // 返回sha
