// Copyright (C) 2025 kleidis

package io.github.kleidis.deltapatcher.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

enum class DialogType {
    ERROR,
    SUCCESS,
    STORAGE_WARNING,
    CONFIRMATION
}

data class DialogAction(
    val text: String,
    val onClick: () -> Unit
)

@Composable
fun PopUpMessageDialog(
    isVisible: Boolean,
    dialogType: DialogType,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    primaryAction: DialogAction? = null,
    secondaryAction: DialogAction? = null
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(
                    onClick = primaryAction?.onClick ?: onDismiss
                ) {
                    Text(primaryAction?.text ?: getDefaultPrimaryButtonText(dialogType))
                }
            },
            dismissButton = if (secondaryAction != null || dialogType == DialogType.STORAGE_WARNING) {
                {
                    TextButton(
                        onClick = secondaryAction?.onClick ?: onDismiss
                    ) {
                        Text("Cancel")
                    }
                }
            } else null
        )
    }
}

private fun getDefaultPrimaryButtonText(dialogType: DialogType): String {
    return when (dialogType) {
        DialogType.ERROR -> "OK"
        DialogType.SUCCESS -> "Finish"
        DialogType.STORAGE_WARNING -> "Continue"
        DialogType.CONFIRMATION -> "OK"
    }
}
