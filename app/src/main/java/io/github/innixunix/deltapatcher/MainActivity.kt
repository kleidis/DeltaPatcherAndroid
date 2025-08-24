// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import io.github.innixunix.deltapatcher.ui.settings.SettingsEntries
import io.github.innixunix.deltapatcher.ui.settings.SettingsMenu

class MainActivity : ComponentActivity() {
    private var isNotificationServiceRunning = false

    val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Toast.makeText(
            this,
            if (isGranted) "Notification permission granted" else "Notification permission denied",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val settingsEntries = remember { SettingsEntries(this) }
            key(settingsEntries.useMonet) {
                DeltaPatcherTheme {
                    DeltaPatcherApp(this)
                }
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {}
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationService.stopService(this)
        isNotificationServiceRunning = false
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun startNotificationService() {
        if (!isNotificationServiceRunning &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !isFinishing && !isDestroyed)) {
            try {
                NotificationService.startService(this)
                isNotificationServiceRunning = true
            } catch (e: Exception) {
                isNotificationServiceRunning = false
            }
        }
    }
    
    fun stopNotificationService() {
        try {
            NotificationService.stopService(this)
            isNotificationServiceRunning = false
        } catch (e: Exception) {
            isNotificationServiceRunning = false
        }
    }
    
    fun exitApp() {
        try {
            if (isNotificationServiceRunning) {
                NotificationService.dismissNotification(this)
                
                NotificationService.stopService(this)
                isNotificationServiceRunning = false
                
                Thread.sleep(200)
                
                FileUtil.clearCache(this)
            }
            
            finish()
            
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        } catch (e: Exception) {
            // If graceful exit fails, force exit
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
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
fun DeltaPatcherApp(mainActivity: MainActivity) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    val settingsEntries = SettingsEntries(LocalContext.current)
    val tabs = listOf("Apply", "Create")
    val context = LocalContext.current
    var isAnyOperationInProgress by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettingsMenu) {
        showSettingsMenu = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { 
                Text(
                    if (showSettingsMenu) "Settings" else "Delta Patcher",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (showSettingsMenu) {
                    IconButton(
                        onClick = { showSettingsMenu = false }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            actions = {
                if (!showSettingsMenu) {
                    IconButton(
                        onClick = {
                            if (!isAnyOperationInProgress) {
                                showSettingsMenu = true
                            }
                        },
                        enabled = !isAnyOperationInProgress
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isAnyOperationInProgress) 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else 
                                MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { mainActivity.exitApp() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Exit App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                actionIconContentColor = MaterialTheme.colorScheme.primary
            )
        )
        
        if (!showSettingsMenu) {
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
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = showSettingsMenu,
                transitionSpec = {
                    if (targetState) {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(300)
                        )
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(300)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300)
                        )
                    }
                },
                label = "settings_transition"
            ) { isInSettings ->
                if (isInSettings) {
                    SettingsMenu(
                        onMonetToggle = {
                            mainActivity.recreate()
                        }
                    )
                } else {
                    when (selectedTabIndex) {
                        0 -> DecodeTab(
                            onOperationStateChange = { isInProgress -> 
                                isAnyOperationInProgress = isInProgress 
                            },
                            onNotificationStart = { mainActivity.startNotificationService() },
                            onNotificationStop = { mainActivity.stopNotificationService() },
                            settingsEntries
                        )
                        1 -> EncodeTab(
                            onOperationStateChange = { isInProgress -> 
                                isAnyOperationInProgress = isInProgress 
                            },
                            onNotificationStart = { mainActivity.startNotificationService() },
                            onNotificationStop = { mainActivity.stopNotificationService() },
                            settingsEntries
                        )
                    }
                }
            }
        }
    }
}