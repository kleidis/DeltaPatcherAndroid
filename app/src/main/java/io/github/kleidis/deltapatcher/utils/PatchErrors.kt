// Copyright (C) 2025 kleidis

package io.github.kleidis.deltapatcher.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.kleidis.deltapatcher.NativeLibrary
import io.github.kleidis.deltapatcher.ui.settings.SettingsEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PatchOperationType {
    DECODE,
    ENCODE
}

data class PatchOperationParams(
    val operationType: PatchOperationType,
    val originalFilePath: String,
    val originalFileName: String,
    val secondaryFilePath: String,
    val secondaryFileName: String,
    val outputDirUri: Uri,
    val outputFileName: String,
    val description: String = "", // encode only
    val settings: SettingsEntries,
    val context: Context,
    val onLogUpdate: (String) -> Unit,
    val onProgressUpdate: (Float, String) -> Unit,
    val onOperationStateChange: (Boolean) -> Unit,
    val onNotificationStart: () -> Unit,
    val onNotificationStop: () -> Unit,
    val onSuccess: () -> Unit,
    val onError: (String) -> Unit
)


suspend fun executeUnifiedPatchOperation(patch: PatchOperationParams): Int {
    return withContext(Dispatchers.IO) {
        try {
            if (patch.originalFilePath.isEmpty() ||
                patch.secondaryFilePath.isEmpty() ||
                patch.outputFileName.isBlank()) {
                withContext(Dispatchers.Main) {
                    patch.onError("Please fill in all required fields")
                }
                return@withContext -1
            }

            // Validate files exist and are not empty
            try {
                val originalFile = File(patch.originalFilePath)
                val secondaryFile = File(patch.secondaryFilePath)

                if (!originalFile.exists()) {
                    withContext(Dispatchers.Main) {
                        patch.onError("Original ROM file not found")
                    }
                    return@withContext -1
                }

                if (!secondaryFile.exists()) {
                    val fileType = if (patch.operationType == PatchOperationType.DECODE) "Patch" else "Modified ROM"
                    withContext(Dispatchers.Main) {
                        patch.onError("$fileType file not found")
                    }
                    return@withContext -1
                }

                if (originalFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        patch.onError("Original ROM file is empty")
                    }
                    return@withContext -1
                }

                if (secondaryFile.length() == 0L) {
                    val fileType = if (patch.operationType == PatchOperationType.DECODE) "Patch" else "Modified ROM"
                    withContext(Dispatchers.Main) {
                        patch.onError("$fileType file is empty")
                    }
                    return@withContext -1
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    patch.onError("Error validating files: ${e.message}")
                }
                return@withContext -1
            }

            val tempOutputPath = File(patch.context.cacheDir, patch.outputFileName).absolutePath

            withContext(Dispatchers.Main) {
                val logMessage = when (patch.operationType) {
                    PatchOperationType.DECODE ->
                        "Original ROM: ${patch.originalFileName}\nPatch file: ${patch.secondaryFileName}\n"
                    PatchOperationType.ENCODE ->
                        "Original ROM: ${patch.originalFileName}\nModified ROM: ${patch.secondaryFileName}\n"
                }
                patch.onLogUpdate(logMessage)
                patch.onOperationStateChange(true)
                patch.onNotificationStart()
            }

            PatchErrors.setLastErrorDetails("")

            val logCallback = object : NativeLibrary.LogCallback {
                override fun onLogUpdate(message: String) {
                    if (message.contains("xdelta3:") || message.contains("error") || message.contains("Error")) {
                        PatchErrors.setLastErrorDetails(message.trim())
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        patch.onLogUpdate(message + "\n")
                    }
                }
            }

            val progressCallback = object : NativeLibrary.ProgressCallback {
                override fun onProgressUpdate(progress: Float, message: String) {
                    CoroutineScope(Dispatchers.Main).launch {
                        patch.onProgressUpdate(progress, message)
                        patch.onLogUpdate("$message\n")
                    }
                }
            }

            val result = when (patch.operationType) {
                PatchOperationType.DECODE -> {
                    NativeLibrary.decode(
                        patch.originalFilePath,
                        tempOutputPath,
                        patch.secondaryFilePath,
                        patch.settings.useChecksum,
                        logCallback,
                        progressCallback
                    )
                }
                PatchOperationType.ENCODE -> {
                    NativeLibrary.encode(
                        patch.originalFilePath,
                        patch.secondaryFilePath,
                        tempOutputPath,
                        patch.description,
                        logCallback,
                        patch.settings.useChecksum,
                        patch.settings.compressionLevel,
                        patch.settings.secondaryCompression,
                        patch.settings.srcWindowSize,
                        progressCallback
                    )
                }
            }

            withContext(Dispatchers.Main) {
                delay(100)
            }

            if (result == 0) {
                try {
                    val tempFile = File(tempOutputPath)
                    if (tempFile.exists()) {
                        withContext(Dispatchers.Main) {
                            patch.onLogUpdate("Output file: ${tempFile.name} (${tempFile.length()} bytes)\n")
                        }

                        val outputDir = DocumentFile.fromTreeUri(patch.context, patch.outputDirUri)
                        val outputFile = outputDir?.createFile("application/octet-stream", patch.outputFileName)

                        if (outputFile != null) {
                            tempFile.inputStream().use { input ->
                                patch.context.contentResolver.openOutputStream(outputFile.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }

                            tempFile.delete()

                            withContext(Dispatchers.Main) {
                                patch.onSuccess()
                                patch.onNotificationStop()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                patch.onLogUpdate("Failed to create output file in selected directory\n")
                                patch.onError("Failed to create output file.\nPlease check if you have write permission to the selected directory.")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            patch.onLogUpdate("Operation reported success but no output file was generated\n")
                            patch.onError("Operation failed - no output file was generated.\nThis may indicate a file system or permission issue.")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        patch.onLogUpdate("Error copying output file: ${e.message}\n")
                        patch.onError("Error copying output file: ${e.message}")
                    }
                }
            } else {
                val errorMsg = PatchErrors.getLastErrorDetails()

                withContext(Dispatchers.Main) {
                    patch.onLogUpdate("Operation failed: $errorMsg\n")
                    patch.onError(errorMsg)
                }
            }

            result
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                patch.onLogUpdate("Error: ${e.message}\n")
                patch.onError("Error: ${e.message}")
            }
            -1
        } finally {
            withContext(Dispatchers.Main) {
                patch.onOperationStateChange(false)
            }
        }
    }
}

object PatchErrors {
    private var lastErrorDetails: String = ""

    fun setLastErrorDetails(details: String) {
        lastErrorDetails = details
    }

    fun getLastErrorDetails(): String {
        return lastErrorDetails
    }
}