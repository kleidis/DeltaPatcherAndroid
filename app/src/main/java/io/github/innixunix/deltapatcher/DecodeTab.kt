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
import kotlinx.coroutines.launch
import java.io.File

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

@Composable
fun DecodeTab() {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
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

    val canApplyPatch by remember {
        derivedStateOf {
            originalFilePath.isNotEmpty() && 
            patchFilePath.isNotEmpty() && 
            outputDirUri != null && 
            outputFileName.isNotBlank()
        }
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
    }

    val originalFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                // Clear previous file if exists
                clearFile(originalFilePath, originalFileIsTemp) {
                    originalFilePath = ""
                    originalFileName = ""
                    originalFileIsTemp = false
                    outputFileName = ""
                }
                
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "Unknown file"

                val realPath = getRealFilePath(context, uri)
                if (realPath != null) {
                    // Most likely unused on Android 11+
                    originalFilePath = realPath
                    originalFileName = fileName
                    originalFileIsTemp = false
                    outputFileName = generateOutputFileName(fileName, "patched")
                } else {
                    val tempPath = copyUriToTempFile(context, uri, "original")
                    if (tempPath != null) {
                        originalFilePath = tempPath
                        originalFileName = fileName
                        originalFileIsTemp = true
                        outputFileName = generateOutputFileName(fileName, "patched")
                    } else {
                        scope.launch {
                            errorMessage = "Failed to access selected file"
                            showErrorDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    errorMessage = "Error processing file: ${e.message}"
                    showErrorDialog = true
                }
            }
        }
    }

    val patchFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Clear previous file if exists
                clearFile(patchFilePath, patchFileIsTemp) {
                    patchFilePath = ""
                    patchFileName = ""
                    patchFileIsTemp = false
                    patchDescription = ""
                }
                
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "Unknown file"
                
                val realPath = getRealFilePath(context, uri)
                if (realPath != null) {
                    patchFilePath = realPath
                    patchFileName = fileName
                    patchFileIsTemp = false
                    
                    if (originalFileName.isNotEmpty()) {
                        outputFileName = generateOutputFileName(originalFileName, "patched")
                    }
                    
                    scope.launch {
                        try {
                            patchDescription = NativeLibrary.getDescription(realPath)
                        } catch (e: Exception) {
                            patchDescription = "No description available"
                        }
                    }
                } else {
                    val tempPath = copyUriToTempFile(context, uri, "patch")
                    if (tempPath != null) {
                        patchFilePath = tempPath
                        patchFileName = fileName
                        patchFileIsTemp = true
                        
                        if (originalFileName.isNotEmpty()) {
                            outputFileName = generateOutputFileName(originalFileName, "patched")
                        }
                        
                        scope.launch {
                            try {
                                patchDescription = NativeLibrary.getDescription(tempPath)
                            } catch (e: Exception) {
                                patchDescription = "No description available"
                            }
                        }
                    } else {
                        scope.launch {
                            errorMessage = "Failed to access selected file"
                            showErrorDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    errorMessage = "Error processing file: ${e.message}"
                    showErrorDialog = true
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
                scope.launch {
                    try {
                        if (originalFilePath.isEmpty() || patchFilePath.isEmpty() || outputDirUri == null || outputFileName.isBlank()) {
                            errorMessage = "Please fill in all required fields"
                            showErrorDialog = true
                            return@launch
                        }

                        try {
                            val originalFile = File(originalFilePath)
                            val patchFile = File(patchFilePath)
                            
                            if (!originalFile.exists()) {
                                errorMessage = "Original ROM file not found"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (!patchFile.exists()) {
                                errorMessage = "Patch file not found"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (originalFile.length() == 0L) {
                                errorMessage = "Original ROM file is empty"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (patchFile.length() == 0L) {
                                errorMessage = "Patch file is empty"
                                showErrorDialog = true
                                return@launch
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error validating files: ${e.message}"
                            showErrorDialog = true
                            return@launch
                        }
                        
                        log = "Original ROM: $originalFileName\n"
                        log += "Patch file: $patchFileName\n"

                        //  temporary output path for the patched ROM
                        val tempOutputPath = File(context.cacheDir, outputFileName).absolutePath
                        isPatching = true

                        val logCallback = object : NativeLibrary.LogCallback {
                            override fun onLogUpdate(message: String) {
                                log += message + "\n"
                            }
                        }
                        
                        val result = NativeLibrary.decode(
                            originalFilePath,
                            tempOutputPath,
                            patchFilePath,
                            logCallback
                        )
                        
                        if (result == 0) {
                            try {
                                val tempFile = File(tempOutputPath)
                                if (tempFile.exists()) {
                                    log += "Output file: ${tempFile.name} (${tempFile.length()} bytes)\n"
                                    
                                    val outputDir = DocumentFile.fromTreeUri(context, outputDirUri!!)
                                    val outputFile = outputDir?.createFile("application/octet-stream", outputFileName)
                                    
                                    if (outputFile != null) {
                                        tempFile.inputStream().use { input ->
                                            context.contentResolver.openOutputStream(outputFile.uri)?.use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        // Clean up temp output file
                                        tempFile.delete()
                                        
                                        showSuccessDialog = true
                                    } else {
                                        errorMessage = "Failed to create output file"
                                        showErrorDialog = true
                                    }
                                } else {
                                    errorMessage = "Patch application failed - no output file generated"
                                    showErrorDialog = true
                                }
                            } catch (e: Exception) {
                                log += "Error copying output file: ${e.message}\n"
                                errorMessage = "Error copying output file: ${e.message}"
                                showErrorDialog = true
                            }
                        } else {
                            // xdelta3 error
                            val xdeltaMessages = log.lowercase()
                            val errorMsg = when {
                                xdeltaMessages.contains("source file too short") || 
                                xdeltaMessages.contains("target window checksum mismatch") ||
                                xdeltaMessages.contains("found 'source file too short'") ||
                                xdeltaMessages.contains("found 'target window checksum mismatch'") -> 
                                    "The patch could not be applied:\nThe file you are trying to patch is not the right one."
                                xdeltaMessages.contains("no such file") ||
                                xdeltaMessages.contains("cannot open") -> 
                                    "File access error - check if files exist and are readable"
                                result == 1 -> "General error occurred during patching"
                                result == 2 -> "Invalid arguments provided to xdelta3"
                                result == 3 -> "Input file error - file may be corrupted or inaccessible"
                                result == 4 -> "Output file error - cannot write to destination"
                                result == 5 -> "The file you are trying to patch is not the right one"
                                result == 6 -> "Memory allocation failed"
                                else -> "Unknown error occurred (code: $result)\n\nFull log:\n$log"
                            }
                            
                            log += "Patching failed: $errorMsg\n"
                            errorMessage = errorMsg
                            showErrorDialog = true
                        }
                    } catch (e: Exception) {
                        log += "Error: ${e.message}\n"
                        errorMessage = "Error: ${e.message}"
                        showErrorDialog = true
                    } finally {
                        isPatching = false
                    }
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
                        if (isPatching) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Processing...", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
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

    // Error Dialog
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

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success") },
            text = { Text("Patch applied successfully!") },
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
