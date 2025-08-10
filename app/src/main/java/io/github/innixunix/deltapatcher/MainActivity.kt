// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher

import android.os.Bundle
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
import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.unit.dp
import java.io.File
import io.github.innixunix.deltapatcher.ui.theme.DeltaPatcherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeltaPatcherTheme {
                DeltaPatcherApp()
            }
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
fun copyUriToTempFile(context: Context, uri: Uri, prefix: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile(prefix, null, context.cacheDir)
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeltaPatcherApp() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Apply" ,"Create")
    val context = LocalContext.current

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
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.weight(1f),
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
            0 -> DecodeTab()
            1 -> EncodeTab()
        }
    }
}