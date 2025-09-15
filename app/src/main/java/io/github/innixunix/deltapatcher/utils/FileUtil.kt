package io.github.innixunix.deltapatcher.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.innixunix.deltapatcher.NativeLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object FileUtil {
    var totalStorageRequired by mutableLongStateOf(0L)

    fun clearCache(context: Context) {
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                try {
                    if (file.isFile) {
                        file.delete()
                    } else if (file.isDirectory) {
                        file.deleteRecursively()
                    }
                } catch (_: Exception) {
                    // ignore individual file deletion failures
                }
            }
        }

        Toast.makeText(context, "Delta Patcher is clearing cache", Toast.LENGTH_SHORT).show()
    }

    fun checkStorageSpace(context: Context): Boolean {
            return try {
                val cacheDir = context.cacheDir
                val freeSpace = cacheDir.freeSpace
                freeSpace > (totalStorageRequired * 2)
            } catch (_: Exception) {
                false
        }
    }

    @Composable
    fun rememberDirectoryPicker(
        onDirectorySelected: (Uri) -> Unit
    ): ManagedActivityResultLauncher<Uri?, Uri?> {
        return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                onDirectorySelected(uri)
            }
        }
    }

    @Composable
    fun filePicker(
        originalFilePath: String,
        originalFileIsTemp: Boolean,
        otherFileName: String = "", // For generating output names (e.g., original name when selecting modified file)
        onFileSelected: (filePath: String, fileName: String, isTemp: Boolean) -> Unit,
        onOutputFileNameChanged: ((String) -> Unit)? = null,
        onPatchDescriptionChanged: ((String) -> Unit)? = null, // Only used for patch files
        onNotificationStart: () -> Unit,
        onError: (String) -> Unit,
        onCopyingStateChanged: (Boolean) -> Unit,
        onCopyProgress: (Float, String) -> Unit,
        removeFromStorageCounter: (String) -> Unit,
        clearFile: (String, Boolean, () -> Unit) -> Unit,
        copyUriToTempFile: suspend (Context, Uri, String, NativeLibrary.ProgressCallback) -> String?,
        addToStorageCounter: (String) -> Unit,
        getRealFilePath: (Context, Uri) -> String?,
        generateOutputFileName: ((String, String) -> String)? = null, // For original files (DecodeTab style)
        generatePatchFileName: ((String, String) -> String)? = null, // For modified files (EncodeTab style)
        filePrefix: String = "original"
    ): ManagedActivityResultLauncher<String, Uri?> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        onNotificationStart()

                        if (originalFilePath.isNotEmpty()) {
                            removeFromStorageCounter(originalFilePath)
                        }
                        clearFile(originalFilePath, originalFileIsTemp) {
                            onFileSelected("", "", false)
                            onOutputFileNameChanged?.invoke("")
                            onPatchDescriptionChanged?.invoke("")
                        }

                        val fileName = context.contentResolver.query(uri, null, null, null, null)
                            ?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            } ?: "Unknown file"

                        val realPath = getRealFilePath(context, uri)
                        if (realPath != null) {
                            onFileSelected(realPath, fileName, false)

                            // Handle output filename generation based on file type
                            when {
                                generateOutputFileName != null -> {
                                    onOutputFileNameChanged?.invoke(
                                        generateOutputFileName(
                                            fileName,
                                            "patched"
                                        )
                                    )
                                }

                                generatePatchFileName != null && otherFileName.isNotEmpty() -> {
                                    onOutputFileNameChanged?.invoke(
                                        generatePatchFileName(
                                            otherFileName,
                                            fileName
                                        )
                                    )
                                }
                            }

                            // Handle patch description extraction (only for patch files)
                            onPatchDescriptionChanged?.let { callback ->
                                try {
                                    callback(NativeLibrary.Companion.getDescription(realPath))
                                } catch (_: Exception) {
                                    callback("No description available")
                                }
                            }
                        } else {
                            onCopyingStateChanged(true)
                            onCopyProgress(0f, "Preparing to copy file...")

                            val progressCallback = object : NativeLibrary.ProgressCallback {
                                override fun onProgressUpdate(progress: Float, message: String) {
                                    onCopyProgress(progress, message)
                                }
                            }

                            val tempPath =
                                copyUriToTempFile(context, uri, filePrefix, progressCallback)
                            if (tempPath != null) {
                                onFileSelected(tempPath, fileName, true)
                                addToStorageCounter(tempPath)

                                // Handle output filename generation for temp files
                                when {
                                    generateOutputFileName != null -> {
                                        onOutputFileNameChanged?.invoke(
                                            generateOutputFileName(
                                                fileName,
                                                "patched"
                                            )
                                        )
                                    }

                                    generatePatchFileName != null && otherFileName.isNotEmpty() -> {
                                        onOutputFileNameChanged?.invoke(
                                            generatePatchFileName(
                                                otherFileName,
                                                fileName
                                            )
                                        )
                                    }
                                }

                                // Handle patch description extraction for temp files
                                onPatchDescriptionChanged?.let { callback ->
                                    try {
                                        callback(NativeLibrary.Companion.getDescription(tempPath))
                                    } catch (_: Exception) {
                                        callback("No description available")
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Failed to access selected file")
                                }
                            }

                            withContext(Dispatchers.Main) {
                                onCopyingStateChanged(false)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Error processing file: ${e.message}")
                            onCopyingStateChanged(false)
                        }
                    }
                }
            }
        }
    }

    fun addToStorageCounter(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            totalStorageRequired += file.length()
        }
    }

    fun removeFromStorageCounter(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            totalStorageRequired -= file.length()
            if (totalStorageRequired < 0) totalStorageRequired = 0
        }
    }

    fun resetStorageCounter() {
        totalStorageRequired = 0
    }

    fun clearFile(path: String, isTemp: Boolean, onClear: () -> Unit) {
        if (isTemp && path.isNotEmpty()) {
            File(path).delete()
        }
        onClear()
        resetStorageCounter()
    }

    fun generateOutputFileName(inputFileName: String, suffix: String): String {
        val lastDotIndex = inputFileName.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            val nameWithoutExtension = inputFileName.substring(0, lastDotIndex)
            val extension = inputFileName.substring(lastDotIndex)
            "${nameWithoutExtension}_${suffix}${extension}"
        } else {
            "${inputFileName}_${suffix}"
        }
    }
}