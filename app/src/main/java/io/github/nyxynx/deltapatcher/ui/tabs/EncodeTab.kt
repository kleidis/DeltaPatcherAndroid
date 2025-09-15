// Copyright (C) 2025 nyxynx

package io.github.nyxynx.deltapatcher.ui.tabs

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.nyxynx.deltapatcher.copyUriToTempFile
import io.github.nyxynx.deltapatcher.getRealFilePath
import io.github.nyxynx.deltapatcher.utils.FileUtil.addToStorageCounter
import io.github.nyxynx.deltapatcher.utils.FileUtil.checkStorageSpace
import io.github.nyxynx.deltapatcher.utils.FileUtil.clearFile
import io.github.nyxynx.deltapatcher.utils.FileUtil.removeFromStorageCounter
import io.github.nyxynx.deltapatcher.utils.FileUtil.resetStorageCounter
import io.github.nyxynx.deltapatcher.ui.dialogs.DialogAction
import io.github.nyxynx.deltapatcher.ui.dialogs.DialogType
import io.github.nyxynx.deltapatcher.ui.dialogs.PopUpMessageDialog
import io.github.nyxynx.deltapatcher.ui.settings.SettingsEntries
import io.github.nyxynx.deltapatcher.utils.FileUtil
import io.github.nyxynx.deltapatcher.utils.PatchOperationParams
import io.github.nyxynx.deltapatcher.utils.PatchOperationType
import io.github.nyxynx.deltapatcher.utils.executeUnifiedPatchOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun generatePatchFileName(originalFileName: String, modifiedFileName: String): String {
    val originalName = originalFileName.substringBeforeLast('.')
    val modifiedName = modifiedFileName.substringBeforeLast('.')
    return "${originalName}_to_${modifiedName}.xdelta"
}

@Composable
fun EncodeTab(
    onOperationStateChange: (Boolean) -> Unit = {},
    onNotificationStart: () -> Unit = {},
    onNotificationStop: () -> Unit = {},
    settings: SettingsEntries = SettingsEntries(LocalContext.current)
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var originalFilePath by rememberSaveable { mutableStateOf("") }
    var originalFileName by rememberSaveable { mutableStateOf("") }
    var originalFileIsTemp by rememberSaveable { mutableStateOf(false) }
    var modifiedFilePath by rememberSaveable { mutableStateOf("") }
    var modifiedFileName by rememberSaveable { mutableStateOf("") }
    var modifiedFileIsTemp by rememberSaveable { mutableStateOf(false) }
    var outputDirUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var outputFileName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    var log by rememberSaveable { mutableStateOf("") }
    var logExpanded by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var showSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var isCreating by rememberSaveable { mutableStateOf(false) }

    var isCopyingOriginal by rememberSaveable { mutableStateOf(false) }
    var originalCopyProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var originalCopyMessage by rememberSaveable { mutableStateOf("") }
    var isCopyingModified by rememberSaveable { mutableStateOf(false) }
    var modifiedCopyProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var modifiedCopyMessage by rememberSaveable { mutableStateOf("") }

    var totalStorageRequired by rememberSaveable { mutableLongStateOf(0L) }
    var showStorageWarning by rememberSaveable { mutableStateOf(false) }

    val canCreatePatch by remember {
        derivedStateOf {
            originalFilePath.isNotEmpty() &&
            modifiedFilePath.isNotEmpty() &&
            outputDirUri != null &&
            outputFileName.isNotBlank()
        }
    }

    suspend fun createPatch() {
        try {
            if (!checkStorageSpace(context)) {
                showStorageWarning = true
                return
            }

            val patch = PatchOperationParams(
                operationType = PatchOperationType.ENCODE,
                originalFilePath = originalFilePath,
                originalFileName = originalFileName,
                secondaryFilePath = modifiedFilePath,
                secondaryFileName = modifiedFileName,
                outputDirUri = outputDirUri!!,
                outputFileName = outputFileName,
                description = description,
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
                    modifiedFilePath = ""
                    modifiedFileName = ""
                    modifiedFileIsTemp = false
                    outputFileName = ""
                    outputDirUri = null
                    description = ""
                    resetStorageCounter()
                },
                onError = { message ->
                    errorMessage = message
                    showErrorDialog = true
                }
            )

            isCreating = true
            executeUnifiedPatchOperation(patch)
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            showErrorDialog = true
        } finally {
            isCreating = false
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
        filePrefix = "original"
    )

    val modifiedFilePicker = FileUtil.filePicker(
        originalFilePath = modifiedFilePath,
        originalFileIsTemp = modifiedFileIsTemp,
        otherFileName = originalFileName,
        onFileSelected = { filePath, fileName, isTemp ->
            modifiedFilePath = filePath
            modifiedFileName = fileName
            modifiedFileIsTemp = isTemp
        },
        onOutputFileNameChanged = { outputFileName = it },
        onNotificationStart = onNotificationStart,
        onError = { message ->
            errorMessage = message
            showErrorDialog = true
        },
        onCopyingStateChanged = { isCopyingModified = it },
        onCopyProgress = { progress, message ->
            modifiedCopyProgress = progress
            modifiedCopyMessage = message
        },
        removeFromStorageCounter = ::removeFromStorageCounter,
        clearFile = ::clearFile,
        copyUriToTempFile = ::copyUriToTempFile,
        addToStorageCounter = ::addToStorageCounter,
        getRealFilePath = ::getRealFilePath,
        generatePatchFileName = ::generatePatchFileName,
        filePrefix = "modified"
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
        if (isCopyingOriginal || isCopyingModified) {
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
            value = modifiedFileName,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Modified ROM File") },
            placeholder = { Text("Select modified ROM file...") },
            readOnly = true,
            trailingIcon = {
                Row {
                    if (modifiedFileName.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                clearFile(modifiedFilePath, modifiedFileIsTemp) {
                                    modifiedFilePath = ""
                                    modifiedFileName = ""
                                    modifiedFileIsTemp = false
                                    outputFileName = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear file")
                        }
                    }
                    IconButton(onClick = { modifiedFilePicker.launch("*/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "Select file")
                    }
                }
            },
            singleLine = true
        )

        if (isCopyingModified) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { modifiedCopyProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = modifiedCopyMessage,
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
            label = { Text("Output Patch File Name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        focusManager.clearFocus()
                    }
                },
            label = { Text("Patch Description (optional)") },
            placeholder = { Text("Describe what this patch does...") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    createPatch()
                }
            },
            enabled = canCreatePatch && !isCreating
        ) {
            if (isCreating) {
                Text("Creating Patch...")
            } else {
                Text("Create Patch")
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
                createPatch()
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
        message = "Patch created successfully!",
        onDismiss = { showSuccessDialog = false }
    )
}
