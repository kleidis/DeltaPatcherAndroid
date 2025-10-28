package io.github.nyxynx.deltapatcher.ui.pages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nyxynx.deltapatcher.R

enum class SetupPageState {
    Incomplete,
    Completed
}

data class SetupPage (
    val title: String,
    val iconResId: Int,
    val description: String,
    val buttonText: String,
    val onButtonClick:() -> Unit,
    var isProcessing: Boolean = false,
    val pageState: MutableState<SetupPageState>
)

class FirstTimeSetup {
    private val notificationPageState = mutableStateOf(SetupPageState.Incomplete)
    private val finishPageState = mutableStateOf(SetupPageState.Completed)


    @Composable
    fun create(context: Context): List<SetupPage> {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            notificationPageState.value = if (isGranted) {
                SetupPageState.Completed
            } else {
                SetupPageState.Completed
            }
        }

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasNotificationPermission) {
            notificationPageState.value = SetupPageState.Completed
        }

        return listOf(
            SetupPage(
                title = "Welcome",
                iconResId = R.drawable.ic_launcher_foreground,
                description = "Welcome to DeltaPatcher, .delta patching frontend for Android.",
                buttonText = "",
                onButtonClick = { /* no-op, proceed to next */ },
                pageState = remember { mutableStateOf(SetupPageState.Completed) }
            ),
            SetupPage(
                title = "Enable Notifications",
                iconResId = R.drawable.ic_notifications,
                description = "Allow notifications to allow the app to be ran in the background more reliably.",
                buttonText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Grant Permission" else "Continue",
                onButtonClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationPageState.value = SetupPageState.Completed
                    }
                },
                pageState = notificationPageState
            ),
        )
    }

    @Composable
    fun SetupPage(page: SetupPage, onNextClick: (() -> Unit)? = null, onBackClick: (() -> Unit)? = null) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = page.iconResId),
                    contentDescription = "${page.title} icon",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = page.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (page.buttonText.isNotEmpty())
                    Button(
                        onClick = {
                            page.isProcessing = true
                            page.onButtonClick()
                            page.isProcessing = false
                        },
                        enabled = !page.isProcessing && page.pageState.value == SetupPageState.Incomplete,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text(if (page.isProcessing) "Processing..." else page.buttonText)
                    }

                if (page.pageState.value == SetupPageState.Completed && page.buttonText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (onBackClick != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Button(
                        onClick = { onBackClick() }
                    ) {
                        Text("Back")
                    }
                }
            }

            if (page.pageState.value == SetupPageState.Completed && onNextClick != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Button(
                        onClick = { onNextClick() }
                    ) {
                        Text(if (page.title == "Setup Complete") "Finish" else "Next" )
                    }
                }
            }
        }
    }
}
