// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.documentfile.provider.DocumentFile
import io.github.innixunix.deltapatcher.ui.settings.SettingsEntries
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

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
    
    // Progress tracking states
    var isCopyingOriginal by rememberSaveable { mutableStateOf(false) }
    var originalCopyProgress by rememberSaveable { mutableStateOf(0f) }
    var originalCopyMessage by rememberSaveable { mutableStateOf("") }
    var isCopyingModified by rememberSaveable { mutableStateOf(false) }
    var modifiedCopyProgress by rememberSaveable { mutableStateOf(0f) }
    var modifiedCopyMessage by rememberSaveable { mutableStateOf("") }

    var totalStorageRequired by rememberSaveable { mutableStateOf(0L) }
    var showStorageWarning by rememberSaveable { mutableStateOf(false) }
    
    val canCreatePatch by remember {
        derivedStateOf {
            originalFilePath.isNotEmpty() && 
            modifiedFilePath.isNotEmpty() && 
            outputDirUri != null && 
            outputFileName.isNotBlank()
        }
    }
    
    val isAnyOperationInProgress by remember {
        derivedStateOf {
            isCopyingOriginal || isCopyingModified || isCreating
        }
    }

    fun checkStorageSpace(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            val freeSpace = cacheDir.freeSpace
            freeSpace > (totalStorageRequired * 2)
        } catch (e: Exception) {
            false
        }
    }
    
    fun addToStorageCounter(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                totalStorageRequired += file.length()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun removeFromStorageCounter(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                totalStorageRequired -= file.length()
                if (totalStorageRequired < 0) totalStorageRequired = 0
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun resetStorageCounter() {
        totalStorageRequired = 0
    }
    
    fun clearFile(path: String, isTemp: Boolean, onClear: () -> Unit) {
        if (isTemp && path.isNotEmpty()) {
            try {
                File(path).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
        onClear()
        resetStorageCounter()
    }
    
    suspend fun createPatch() {
        try {
            if (originalFilePath.isEmpty() || modifiedFilePath.isEmpty() || outputDirUri == null || outputFileName.isBlank()) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Please fill in all required fields"
                    showErrorDialog = true
                }
                return
            }

            if (!checkStorageSpace()) {
                withContext(Dispatchers.Main) {
                    showStorageWarning = true
                }
                return
            }

            try {
                val originalFile = File(originalFilePath)
                val modifiedFile = File(modifiedFilePath)
                
                if (!originalFile.exists()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Original ROM file not found"
                        showErrorDialog = true
                    }
                    return
                }
                
                if (!modifiedFile.exists()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Modified ROM file not found"
                        showErrorDialog = true
                    }
                    return
                }
                
                if (originalFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Original ROM file is empty"
                        showErrorDialog = true
                    }
                    return
                }
                
                if (modifiedFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Modified ROM file is empty"
                        showErrorDialog = true
                    }
                    return
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error validating files: ${e.message}"
                    showErrorDialog = true
                }
                return
            }
            
            val tempOutputPath = File(context.cacheDir, outputFileName).absolutePath
            
            withContext(Dispatchers.Main) {
                log = "Original ROM: $originalFileName\n"
                log += "Modified ROM: $modifiedFileName\n"
                isCreating = true
                onOperationStateChange(true)
                onNotificationStart()
            }

            val logCallback = object : NativeLibrary.LogCallback {
                override fun onLogUpdate(message: String) {
                    scope.launch(Dispatchers.Main) {
                        log += message + "\n"
                    }
                }
            }

            val progressCallback = object : NativeLibrary.ProgressCallback {
                override fun onProgressUpdate(progress: Float, message: String) {
                    scope.launch(Dispatchers.Main) {
                        log += "$message\n"
                    }
                }
            }
            
            val result = NativeLibrary.encode(
                originalFilePath,
                modifiedFilePath,
                tempOutputPath,
                description,
                logCallback,
                settings.useChecksum,
                settings.compressionLevel,
                settings.secondaryCompression,
                settings.srcWindowSize,
                progressCallback
                )

            withContext(Dispatchers.Main) {
                delay(100)
            }

            if (result == 0) {
                try {
                    val tempFile = File(tempOutputPath)
                    if (tempFile.exists()) {
                        withContext(Dispatchers.Main) {
                            log += "Output file: ${tempFile.name} (${tempFile.length()} bytes)\n"
                        }
                        
                        val outputDir = DocumentFile.fromTreeUri(context, outputDirUri!!)
                        val outputFile = outputDir?.createFile("application/octet-stream", outputFileName)
                        
                        if (outputFile != null) {
                            tempFile.inputStream().use { input ->
                                context.contentResolver.openOutputStream(outputFile.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            tempFile.delete()
                            
                            withContext(Dispatchers.Main) {
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
                                onNotificationStop()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                log += "Patch creation reported success but no output file was generated\n"
                                errorMessage = "Patch creation failed - no output file generated.\nThis may indicate an issue with the input files or compression settings."
                                showErrorDialog = true
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Patch creation failed - no output file generated"
                            showErrorDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        log += "Error copying output file: ${e.message}\n"
                        errorMessage = "Error copying output file: ${e.message}"
                        showErrorDialog = true
                    }
                }
            } else {
                val xdeltaMessages = log.lowercase()
                val errorMsg = when {
                    xdeltaMessages.contains("no such file") ||
                    xdeltaMessages.contains("cannot open") -> 
                        "File access error - check if files exist and are readable"
                    result == 1 -> "General error occurred during patch creation"
                    result == 2 -> "Invalid arguments provided to xdelta3"
                    result == 3 -> "Input file error - file may be corrupted or inaccessible"
                    result == 4 -> "Output file error - cannot write to destination"
                    result == 5 -> "Memory allocation failed"
                    else -> "Unknown error occurred (code: $result)\n\nFull log:\n$log"
                }
                
                withContext(Dispatchers.Main) {
                    log += "Patch creation failed: $errorMsg\n"
                    errorMessage = errorMsg
                    showErrorDialog = true
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                log += "Error: ${e.message}\n"
                errorMessage = "Error: ${e.message}"
                showErrorDialog = true
            }
        } finally {
            withContext(Dispatchers.Main) {
                isCreating = false
                onOperationStateChange(false)
            }
        }
    }

    val originalFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    // Start notification service for file processing
                    onNotificationStart()
                    
                    if (originalFilePath.isNotEmpty()) {
                        removeFromStorageCounter(originalFilePath)
                    }
                    clearFile(originalFilePath, originalFileIsTemp) {
                        originalFilePath = ""
                        originalFileName = ""
                        originalFileIsTemp = false
                        outputFileName = ""
                    }
                    
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: "Unknown file"
                    
                    val realPath = getRealFilePath(context, uri)
                    if (realPath != null) {
                        originalFilePath = realPath
                        originalFileName = fileName
                        originalFileIsTemp = false
                        
                        if (modifiedFileName.isNotEmpty()) {
                            outputFileName = generatePatchFileName(fileName, modifiedFileName)
                        }
                    } else {
                        isCopyingOriginal = true
                        originalCopyProgress = 0f
                        originalCopyMessage = "Preparing to copy file..."

                        val progressCallback = object : NativeLibrary.ProgressCallback {
                            override fun onProgressUpdate(progress: Float, message: String) {
                                originalCopyProgress = progress
                                originalCopyMessage = message
                            }
                        }
                        
                        val tempPath = copyUriToTempFile(context, uri, "original", progressCallback)
                        if (tempPath != null) {
                            originalFilePath = tempPath
                            originalFileName = fileName
                            originalFileIsTemp = true
                            addToStorageCounter(tempPath)
                            
                            if (modifiedFileName.isNotEmpty()) {
                                outputFileName = generatePatchFileName(fileName, modifiedFileName)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                errorMessage = "Failed to access selected file"
                                showErrorDialog = true
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            isCopyingOriginal = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error processing file: ${e.message}"
                        showErrorDialog = true
                        isCopyingOriginal = false
                    }
                }
            }
        }
    }

    val modifiedFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    // Start notification service for file processing
                    onNotificationStart()
                    
                    if (modifiedFilePath.isNotEmpty()) {
                        removeFromStorageCounter(modifiedFilePath)
                    }
                    clearFile(modifiedFilePath, modifiedFileIsTemp) {
                        modifiedFilePath = ""
                        modifiedFileName = ""
                        modifiedFileIsTemp = false
                        outputFileName = ""
                    }
                    
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: "Unknown file"
                    
                    val realPath = getRealFilePath(context, uri)
                    if (realPath != null) {
                        modifiedFilePath = realPath
                        modifiedFileName = fileName
                        modifiedFileIsTemp = false
                        
                        if (originalFileName.isNotEmpty()) {
                            outputFileName = generatePatchFileName(originalFileName, fileName)
                        }
                    } else {
                        isCopyingModified = true
                        modifiedCopyProgress = 0f
                        modifiedCopyMessage = "Preparing to copy file..."

                        val progressCallback = object : NativeLibrary.ProgressCallback {
                            override fun onProgressUpdate(progress: Float, message: String) {
                                modifiedCopyProgress = progress
                                modifiedCopyMessage = message
                            }
                        }
                        
                        val tempPath = copyUriToTempFile(context, uri, "modified", progressCallback)
                        if (tempPath != null) {
                            modifiedFilePath = tempPath
                            modifiedFileName = fileName
                            modifiedFileIsTemp = true
                            addToStorageCounter(tempPath)
                            
                            if (originalFileName.isNotEmpty()) {
                                outputFileName = generatePatchFileName(originalFileName, fileName)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                errorMessage = "Failed to access selected file"
                                showErrorDialog = true
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            isCopyingModified = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error processing file: ${e.message}"
                        showErrorDialog = true
                        isCopyingModified = false
                    }
                }
            }
        }
    }

    val outputDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            outputDirUri = uri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { modifier ->
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
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

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showErrorDialog = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showStorageWarning) {
        AlertDialog(
            onDismissRequest = { showStorageWarning = false },
            title = { Text("Storage Space Warning") },
            text = { 
                val requiredMB = (totalStorageRequired * 2) / (1024 * 1024)
                val freeMB = context.cacheDir.freeSpace / (1024 * 1024)
                Text(
                    "This operation requires approximately ${requiredMB}MB of storage space.\n\n" +
                    "Available space: ${freeMB}MB\n\n" +
                    "The operation may fail if there isn't enough space. Continue anyway?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showStorageWarning = false
                        // Continue with the operation
                        scope.launch {
                            createPatch()
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStorageWarning = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success") },
            text = { Text("Patch created successfully!") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showSuccessDialog = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}
