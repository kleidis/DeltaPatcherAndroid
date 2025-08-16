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

fun generatePatchFileName(originalFileName: String, modifiedFileName: String): String {
    val originalName = originalFileName.substringBeforeLast('.')
    val modifiedName = modifiedFileName.substringBeforeLast('.')
    return "${originalName}_to_${modifiedName}.xdelta"
}

@Composable
fun EncodeTab() {
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

    val canCreatePatch by remember {
        derivedStateOf {
            originalFilePath.isNotEmpty() && 
            modifiedFilePath.isNotEmpty() && 
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
                    val tempPath = copyUriToTempFile(context, uri, "original")
                    if (tempPath != null) {
                        originalFilePath = tempPath
                        originalFileName = fileName
                        originalFileIsTemp = true
                        
                        if (modifiedFileName.isNotEmpty()) {
                            outputFileName = generatePatchFileName(fileName, modifiedFileName)
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

    val modifiedFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
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
                    val tempPath = copyUriToTempFile(context, uri, "modified")
                    if (tempPath != null) {
                        modifiedFilePath = tempPath
                        modifiedFileName = fileName
                        modifiedFileIsTemp = true
                        
                        if (originalFileName.isNotEmpty()) {
                            outputFileName = generatePatchFileName(originalFileName, fileName)
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
        // Original File Selection
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
                scope.launch {
                    try {
                        if (originalFilePath.isEmpty() || modifiedFilePath.isEmpty() || outputDirUri == null || outputFileName.isBlank()) {
                            errorMessage = "Please fill in all required fields"
                            showErrorDialog = true
                            return@launch
                        }

                        try {
                            val originalFile = File(originalFilePath)
                            val modifiedFile = File(modifiedFilePath)
                            
                            if (!originalFile.exists()) {
                                errorMessage = "Original ROM file not found"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (!modifiedFile.exists()) {
                                errorMessage = "Modified ROM file not found"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (originalFile.length() == 0L) {
                                errorMessage = "Original ROM file is empty"
                                showErrorDialog = true
                                return@launch
                            }
                            
                            if (modifiedFile.length() == 0L) {
                                errorMessage = "Modified ROM file is empty"
                                showErrorDialog = true
                                return@launch
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error validating files: ${e.message}"
                            showErrorDialog = true
                            return@launch
                        }
                        
                        log = "Original ROM: $originalFileName\n"
                        log += "Modified ROM: $modifiedFileName\n"

                        val tempOutputPath = File(context.cacheDir, outputFileName).absolutePath
                        isCreating = true

                        val logCallback = object : NativeLibrary.LogCallback {
                            override fun onLogUpdate(message: String) {
                                log += message + "\n"
                            }
                        }
                        
                        val result = NativeLibrary.encode(
                            originalFilePath,
                            modifiedFilePath,
                            tempOutputPath,
                            description,
                            logCallback
                        )
                        
                        if (result == 0) {
                            try {
                                val tempFile = File(tempOutputPath)
                                if (tempFile.exists()) {
                                    log += "Output patch: ${tempFile.name} (${tempFile.length()} bytes)\n"
                                    
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
                                    errorMessage = "Patch creation failed - no output file generated"
                                    showErrorDialog = true
                                }
                            } catch (e: Exception) {
                                log += "Error copying output file: ${e.message}\n"
                                errorMessage = "Error copying output file: ${e.message}"
                                showErrorDialog = true
                            }
                        } else {
                            //  xdelta3 errors
                            val errorMsg = when (result) {
                                1 -> "General error occurred during patch creation"
                                2 -> "Invalid arguments provided to xdelta3"
                                3 -> "Input file error - file may be corrupted or inaccessible"
                                4 -> "Output file error - cannot write to destination"
                                5 -> "Files are identical - no patch needed"
                                6 -> "Memory allocation failed"
                                else -> "Unknown error occurred (code: $result)"
                            }
                            
                            log += "Patch creation failed: $errorMsg\n"
                            errorMessage = errorMsg
                            showErrorDialog = true
                        }
                    } catch (e: Exception) {
                        log += "Error: ${e.message}\n"
                        errorMessage = "Error: ${e.message}"
                        showErrorDialog = true
                    } finally {
                        isCreating = false
                    }
                }
            },
            enabled = canCreatePatch && !isCreating
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
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
                        if (isCreating) {
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
