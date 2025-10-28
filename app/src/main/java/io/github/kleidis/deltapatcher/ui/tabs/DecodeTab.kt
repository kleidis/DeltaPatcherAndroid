// Copyright (C) 2025 kleidis

package io.github.kleidis.deltapatcher.ui.tabs

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.kleidis.deltapatcher.copyUriToTempFile
import io.github.kleidis.deltapatcher.getRealFilePath
import io.github.kleidis.deltapatcher.utils.FileUtil.addToStorageCounter
import io.github.kleidis.deltapatcher.utils.FileUtil.checkStorageSpace
import io.github.kleidis.deltapatcher.utils.FileUtil.clearFile
import io.github.kleidis.deltapatcher.utils.FileUtil.generateOutputFileName
import io.github.kleidis.deltapatcher.utils.FileUtil.removeFromStorageCounter
import io.github.kleidis.deltapatcher.utils.FileUtil.resetStorageCounter
import io.github.kleidis.deltapatcher.utils.FileUtil.totalStorageRequired
import io.github.kleidis.deltapatcher.ui.dialogs.DialogAction
import io.github.kleidis.deltapatcher.ui.dialogs.DialogType
import io.github.kleidis.deltapatcher.ui.dialogs.PopUpMessageDialog
import io.github.kleidis.deltapatcher.ui.settings.SettingsEntries
import io.github.kleidis.deltapatcher.utils.FileUtil
import io.github.kleidis.deltapatcher.utils.PatchOperationParams
import io.github.kleidis.deltapatcher.utils.PatchOperationType
import io.github.kleidis.deltapatcher.utils.executeUnifiedPatchOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun DecodeTab(
    onOperationStateChange: (Boolean) -> Unit = {},
    onNotificationStart: () -> Unit = {},
    onNotificationStop: () -> Unit = {},
    settings: SettingsEntries = SettingsEntries(LocalContext.current),
    context: Context = LocalContext.current
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var originalFilePath by rememberSaveable { mutableStateOf("") }
    var originalFileName by rememberSaveable { mutableStateOf("") }
    var originalFileIsTemp by rememberSaveable { mutableStateOf(false) }
    var patchFilePath by rememberSaveable { mutableStateOf("") }
    var patchFileName by rememberSaveable { mutableStateOf("") }
    var patchFileIsTemp by rememberSaveable { mutableStateOf(false) }
    var outputDirUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var outputFileName by rememberSaveable { mutableStateOf("") }
    var patchDescription by rememberSaveable { mutableStateOf("") }

    var log by rememberSaveable { mutableStateOf("") }
    var logExpanded by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var showSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var isPatching by rememberSaveable { mutableStateOf(false) }

    var isCopyingOriginal by rememberSaveable { mutableStateOf(false) }
    var originalCopyProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var originalCopyMessage by rememberSaveable { mutableStateOf("") }
    var isCopyingPatch by rememberSaveable { mutableStateOf(false) }
    var patchCopyProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var patchCopyMessage by rememberSaveable { mutableStateOf("") }

    val canApplyPatch by remember {
        derivedStateOf {
            originalFilePath.isNotEmpty() &&
            patchFilePath.isNotEmpty() &&
            outputDirUri != null &&
            outputFileName.isNotBlank()
        }
    }

    val isAnyOperationInProgress by remember {
        derivedStateOf {
            isCopyingOriginal || isCopyingPatch
        }
    }

    var showStorageWarning by rememberSaveable { mutableStateOf(false) }

    suspend fun applyPatch() {
        try {
            if (!checkStorageSpace(context)) {
                showStorageWarning = true
                return
            }

            val patch = PatchOperationParams(
                operationType = PatchOperationType.DECODE,
                originalFilePath = originalFilePath,
                originalFileName = originalFileName,
                secondaryFilePath = patchFilePath,
                secondaryFileName = patchFileName,
                outputDirUri = outputDirUri!!,
                outputFileName = outputFileName,
                description = "", // Not used for decode
                settings = settings,
                context = context,
                onLogUpdate = { message -> log += message },
                onProgressUpdate = { _, message -> log += "$message\n" },
                onOperationStateChange = onOperationStateChange,
                onNotificationStart = onNotificationStart,
                onNotificationStop = onNotificationStop,
                onSuccess = {
                    showSuccessDialog = true
                    originalFilePath = ""
                    originalFileName = ""
                    originalFileIsTemp = false
                    patchFilePath = ""
                    patchFileName = ""
                    patchFileIsTemp = false
                    outputFileName = ""
                    outputDirUri = null
                    resetStorageCounter()
                },
                onError = { message ->
                    errorMessage = message
                    showErrorDialog = true
                }
            )

            isPatching = true
            executeUnifiedPatchOperation(patch)
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            showErrorDialog = true
        } finally {
            isPatching = false
        }
    }

    val originalFilePicker = FileUtil.filePicker(
        originalFilePath = originalFilePath,
        originalFileIsTemp = originalFileIsTemp,
        onFileSelected = { filePath, fileName, isTemp ->
            originalFilePath = filePath
            originalFileName = fileName
            originalFileIsTemp = isTemp
        },
        onOutputFileNameChanged = { outputFileName = it },
        onNotificationStart = onNotificationStart,
        onError = { message ->
            errorMessage = message
            showErrorDialog = true
        },
        onCopyingStateChanged = { isCopyingOriginal = it },
        onCopyProgress = { progress, message ->
            originalCopyProgress = progress
            originalCopyMessage = message
        },
        removeFromStorageCounter = ::removeFromStorageCounter,
        clearFile = ::clearFile,
        copyUriToTempFile = ::copyUriToTempFile,
        addToStorageCounter = ::addToStorageCounter,
        getRealFilePath = ::getRealFilePath,
        generateOutputFileName = ::generateOutputFileName,
        filePrefix = "original"
    )

    val patchFilePicker = FileUtil.filePicker(
        originalFilePath = patchFilePath,
        originalFileIsTemp = patchFileIsTemp,
        otherFileName = originalFileName,
        onFileSelected = { filePath, fileName, isTemp ->
            patchFilePath = filePath
            patchFileName = fileName
            patchFileIsTemp = isTemp
        },
        onPatchDescriptionChanged = { patchDescription = it },
        onOutputFileNameChanged = { outputFileName = it },
        onNotificationStart = onNotificationStart,
        onError = { message ->
            errorMessage = message
            showErrorDialog = true
        },
        onCopyingStateChanged = { isCopyingPatch = it },
        onCopyProgress = { progress, message ->
            patchCopyProgress = progress
            patchCopyMessage = message
        },
        removeFromStorageCounter = ::removeFromStorageCounter,
        clearFile = ::clearFile,
        copyUriToTempFile = ::copyUriToTempFile,
        addToStorageCounter = ::addToStorageCounter,
        getRealFilePath = ::getRealFilePath,
        generateOutputFileName = ::generateOutputFileName,
        filePrefix = "patch"
    )

    val outputDirPicker = FileUtil.rememberDirectoryPicker { uri ->
        outputDirUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { modifier ->
                val configuration = LocalConfiguration.current
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    modifier.windowInsetsPadding(WindowInsets.displayCutout)
                } else {
                    modifier
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isAnyOperationInProgress) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Copying files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        OutlinedTextField(
            value = originalFileName,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Original ROM File") },
            placeholder = { Text("Select original ROM file...") },
            readOnly = true,
            trailingIcon = {
                Row {
                    if (originalFileName.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                clearFile(originalFilePath, originalFileIsTemp) {
                                    originalFilePath = ""
                                    originalFileName = ""
                                    originalFileIsTemp = false
                                    outputFileName = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear file")
                        }
                    }
                    IconButton(onClick = { originalFilePicker.launch("*/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "Select file")
                    }
                }
            },
            singleLine = true
        )

        if (isCopyingOriginal) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { originalCopyProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = originalCopyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = patchFileName,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Patch File (.xdelta)") },
            placeholder = { Text("Select patch file...") },
            readOnly = true,
            trailingIcon = {
                Row {
                    if (patchFileName.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                clearFile(patchFilePath, patchFileIsTemp) {
                                    patchFilePath = ""
                                    patchFileName = ""
                                    patchFileIsTemp = false
                                    patchDescription = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear file")
                        }
                    }
                    IconButton(onClick = {
                        patchFilePicker.launch("application/octet-stream")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Select file")
                    }
                }
            },
            singleLine = true
        )

        if (isCopyingPatch) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { patchCopyProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = patchCopyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = if (outputDirUri == null) "" else "Directory selected",
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Output Directory") },
            placeholder = { Text("Select output directory...") },
            readOnly = true,
            trailingIcon = {
                Row {
                    if (outputDirUri != null) {
                        IconButton(
                            onClick = { outputDirUri = null }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear directory")
                        }
                    }
                    IconButton(onClick = { outputDirPicker.launch(null) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Select directory")
                    }
                }
            },
            singleLine = true
        )

        OutlinedTextField(
            value = outputFileName,
            onValueChange = { outputFileName = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        focusManager.clearFocus()
                    }
                },
            label = { Text("Output ROM File Name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )

        // read-only, shows patch info
        OutlinedTextField(
            value = patchDescription,
            onValueChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            label = { Text("Patch Description") },
            placeholder = { Text("Select a patch file to see description... (If available)") },
            readOnly = true,
            maxLines = 4
        )

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    applyPatch()
                }
            },
            enabled = canApplyPatch && !isPatching
        ) {
            if (isPatching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Applying Patch...")
            } else {
                Text("Apply Patch")
            }
        }

        OutlinedCard(
            onClick = { logExpanded = !logExpanded }
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        if (logExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        "Expand log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = logExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = log,
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        readOnly = true,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    }

    PopUpMessageDialog(
        isVisible = showErrorDialog,
        dialogType = DialogType.ERROR,
        title = "Error",
        message = errorMessage,
        onDismiss = { showErrorDialog = false }
    )

    PopUpMessageDialog(
        isVisible = showStorageWarning,
        dialogType = DialogType.STORAGE_WARNING,
        title = "Storage Space Warning",
        message = run {
            val requiredMB = (totalStorageRequired * 2) / (1024 * 1024)
            val freeMB = context.cacheDir.freeSpace / (1024 * 1024)
            "This operation requires approximately ${requiredMB}MB of storage space.\n\n" +
            "Available space: ${freeMB}MB\n\n" +
            "The operation may fail if there isn't enough space. Continue anyway?"
        },
        onDismiss = { showStorageWarning = false },
        primaryAction = DialogAction("Continue") {
            showStorageWarning = false
            scope.launch {
                applyPatch()
            }
        },
        secondaryAction = DialogAction("Cancel") {
            showStorageWarning = false
        }
    )

    PopUpMessageDialog(
        isVisible = showSuccessDialog,
        dialogType = DialogType.SUCCESS,
        title = "Success",
        message = "Patch applied successfully!",
        onDismiss = { showSuccessDialog = false }
    )
}
