package com.github.capntrips.kernelflasher.ui.screens.updates

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.room.Room
import com.github.capntrips.kernelflasher.common.types.room.AppDatabase
import com.github.capntrips.kernelflasher.common.types.room.updates.Update
import com.github.capntrips.kernelflasher.common.types.room.updates.UpdateSerializer
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@ExperimentalSerializationApi
class UpdatesViewModel(
    context: Context,
    @Suppress("unused") private val fileSystemManager: FileSystemManager,
    private val navController: NavController,
    private val _isRefreshing: MutableState<Boolean>
) : ViewModel() {
    companion object {
        const val TAG: String = "KernelFlasher/UpdatesState"
        val lastUpdatedFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        private val client = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
            .build()
    }
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "kernel-flasher").build()
    private val updateDao = db.updateDao()
    private val _updates: SnapshotStateList<Update> = mutableStateListOf()

    var currentUpdate: Update? = null
    var changelog: String? = null

    private val _downloadProgress = mutableStateOf<Float?>(null)
    private val _isDownloading = mutableStateOf(false)
    val downloadProgress: Float?
        get() = _downloadProgress.value
    val isDownloading: Boolean
        get() = _isDownloading.value

    val updates: List<Update>
        get() = _updates
    val isRefreshing: Boolean
        get() = _isRefreshing.value

    init {
        launch {
            val updates = updateDao.getAll()
            viewModelScope.launch(Dispatchers.Main) {
                _updates.addAll(updates)
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isRefreshing.value = true }
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Unknown error", e)
                withContext(Dispatchers.Main) {
                    navController.navigate("error/${Uri.encode(e.message ?: "Unknown error")}") {
                        popUpTo("main")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { _isRefreshing.value = false }
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun log(context: Context, message: String, shouldThrow: Boolean = false) {
        Log.d(TAG, message)
        if (!shouldThrow) {
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } else {
            throw Exception(message)
        }
    }

    fun clearCurrent() {
        currentUpdate = null
        changelog = null
    }

    private fun requireHttpUrl(rawUrl: String): String {
        val parsed = Uri.parse(rawUrl.trim())
        require(parsed.scheme.equals("https", ignoreCase = true) || parsed.scheme.equals("http", ignoreCase = true)) {
            "Invalid update URL"
        }
        require(!parsed.host.isNullOrBlank()) { "Invalid update URL" }
        return parsed.toString()
    }

    fun add(url: String, callback: (updateId: Int) -> Unit) {
        launch {
            val parsed = requireHttpUrl(url)

            val request = Request.Builder()
                .url(parsed)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body.string()
                val update: Update = Json.decodeFromString(UpdateSerializer, body)
                update.updateUri = url
                update.lastUpdated = Date()
                val updateId = updateDao.insert(update).toInt()
                val inserted = updateDao.load(updateId)
                withContext (Dispatchers.Main) {
                    _updates.add(inserted)
                    callback.invoke(updateId)
                }
            }
        }
    }

    fun update() {
        launch {
            val selected = currentUpdate ?: throw IllegalStateException("No update selected")
            val updateUrl = selected.updateUri ?: throw IllegalStateException("Update URL is missing")
            val request = Request.Builder().url(requireHttpUrl(updateUrl)).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body.string()
                val update: Update = Json.decodeFromString(UpdateSerializer, body)
                currentUpdate?.let { current ->
                    withContext(Dispatchers.Main) {
                        current.kernelName = update.kernelName
                        current.kernelVersion = update.kernelVersion
                        current.kernelLink = update.kernelLink
                        current.kernelChangelogUrl = update.kernelChangelogUrl
                        current.kernelDate = update.kernelDate
                        current.kernelSha1 = update.kernelSha1
                        current.supportLink = update.supportLink
                        current.lastUpdated = Date()
                        viewModelScope.launch(Dispatchers.IO) {
                            updateDao.update(current)
                        }
                    }
                }
            }
        }
    }

    fun downloadChangelog(callback: () -> Unit) {
        launch {
            val selected = currentUpdate ?: throw IllegalStateException("No update selected")
            val changelogUrl = selected.kernelChangelogUrl
            val request = Request.Builder().url(requireHttpUrl(changelogUrl)).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                changelog = response.body.string()
                withContext (Dispatchers.Main) {
                    callback.invoke()
                }
            }
        }
    }

    private fun insertDownload(context: Context, filename: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        return resolver.insert(MediaStore.Files.getContentUri("external"), values)
    }

    fun downloadKernel(context: Context) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0f
        launch {
            val selected = currentUpdate ?: throw IllegalStateException("No update selected")
            val remoteUrl = requireHttpUrl(selected.kernelLink)
            val filename = (Uri.parse(remoteUrl).lastPathSegment ?: "kernel.zip")
                .replace(Regex("[\\/:*?\"<>|\u0000-\u001F]"), "_")
                .ifBlank { "kernel.zip" }
            val uri = insertDownload(context, filename) ?: throw IOException("Unable to create Downloads file")
            try {
                val request = Request.Builder()
                    .url(remoteUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body
                    val total = body.contentLength()
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Unable to open Downloads file")

                    body.byteStream().use { inputStream ->
                        output.use { outputStream ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            var lastProgressUpdate = 0L
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read < 0) break
                                outputStream.write(buffer, 0, read)
                                downloaded += read

                                if (total > 0L) {
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastProgressUpdate >= 100L || downloaded == total) {
                                        lastProgressUpdate = now
                                        val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                        withContext(Dispatchers.Main.immediate) {
                                            _downloadProgress.value = progress
                                        }
                                    }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        _downloadProgress.value = 1f
                    }
                    log(context, "Saved $filename to Downloads")
                }
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    _isDownloading.value = false
                    _downloadProgress.value = null
                }
            }
        }
    }

    fun delete(callback: () -> Unit) {
        launch {
            val selected = currentUpdate ?: throw IllegalStateException("No update selected")
            updateDao.delete(selected)
            withContext(Dispatchers.Main) {
                _updates.remove(selected)
                currentUpdate = null
                changelog = null
                callback.invoke()
            }
        }
    }
    override fun onCleared() {
        db.close()
        super.onCleared()
    }
}
