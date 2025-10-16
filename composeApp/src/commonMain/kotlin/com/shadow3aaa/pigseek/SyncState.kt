package com.shadow3aaa.pigseek

sealed class SyncState {
    data object Idle : SyncState()
    data object FetchingMetadata : SyncState()
    data class Downloading(val progress: Float) : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
    data object Completed : SyncState()
}
