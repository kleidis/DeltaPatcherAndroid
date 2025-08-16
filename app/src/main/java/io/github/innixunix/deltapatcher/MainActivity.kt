// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import java.io.File
import io.github.innixunix.deltapatcher.ui.theme.DeltaPatcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeltaPatcherTheme {
                DeltaPatcherApp()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        clearCache()
    }
    
    fun clearCache() {
        try {
            val cacheDir = cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isFile) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}



// Just left as a fallback for older Android versions or file URIs
fun getRealFilePath(context: android.content.Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    return null
}

// Copy files to internal app storage in order for xdelta3 to access them
// This is necessary because xdelta3 cannot access files from external storage or content URIs directly
suspend fun copyUriToTempFile(
    context: Context, 
    uri: Uri, 
    prefix: String, 
    progressCallback: NativeLibrary.ProgressCallback? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        
        val fileSize = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        } ?: 0L
        
        val tempFile = File.createTempFile(prefix, null, context.cacheDir)
        
        if (fileSize > 0) {
            var bytesRead = 0L
            val buffer = ByteArray(8192)
            
            tempFile.outputStream().use { outputStream ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    
                    val progress = (bytesRead.toFloat() / fileSize).coerceIn(0f, 1f)
                    progressCallback?.onProgressUpdate(progress, "Copying file... ${(progress * 100).toInt()}%")
                }
            }
        } else {
            progressCallback?.onProgressUpdate(0f, "Copying file...")
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            progressCallback?.onProgressUpdate(1f, "File copied successfully")
        }
        
        tempFile.absolutePath
    } catch (e: Exception) {
        progressCallback?.onProgressUpdate(0f, "Error: ${e.message}")
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeltaPatcherApp() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Apply", "Create")
    val context = LocalContext.current
    var isAnyOperationInProgress by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { 
                Text(
                    "Delta Patcher",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ) 
            },
            actions = {
                IconButton(
                    onClick = {
                        // TODO: Implement settings
                        Toast.makeText(context, "To be implemented", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                actionIconContentColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                TextButton(
                    onClick = { 
                        if (!isAnyOperationInProgress) {
                            selectedTabIndex = index 
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isAnyOperationInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (selectedTabIndex == index) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        when (selectedTabIndex) {
            0 -> DecodeTab(onOperationStateChange = { isInProgress -> 
                isAnyOperationInProgress = isInProgress 
            })
            1 -> EncodeTab(onOperationStateChange = { isInProgress -> 
                isAnyOperationInProgress = isInProgress 
            })
        }
    }
}