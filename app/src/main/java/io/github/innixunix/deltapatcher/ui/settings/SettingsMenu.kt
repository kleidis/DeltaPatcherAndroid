// Copyright (C) 2025 Innixunix

package io.github.innixunix.deltapatcher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenu(
    onMonetToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsEntries(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionHeader("Theme Settings")
        }
        
        item {
            SettingsSwitch(
                title = "Use Material You",
                description = "Use dynamic colors based on your wallpaper",
                checked = settingsManager.useMonet,
                onCheckedChange = { 
                    settingsManager.updateUseMonet(it)
                    onMonetToggle()
                }
            )
        }

        item {
            SettingsSectionHeader("Compression Settings")
        }

        item {
            SettingsSwitch(
                title = "Use Checksum",
                description = "Check for / Include checksums in patches for integrity verification",
                checked = settingsManager.useChecksum,
                onCheckedChange = { settingsManager.updateUseChecksum(it) }
            )
        }


        item {
            SettingsRadioGroup(
                title = "Main Compression Level",
                description = "Higher levels provide better compression but take longer",
                options = SettingsEntries.COMPRESSION_LEVELS,
                selectedValue = settingsManager.compressionLevel,
                onValueChange = { settingsManager.updateCompressionLevel(it) }
            )
        }

        item {
            SettingsRadioGroup(
                title = "Secondary Compression",
                description = "Additional compression algorithm to apply",
                options = SettingsEntries.SECONDARY_COMPRESSIONS,
                selectedValue = settingsManager.secondaryCompression,
                onValueChange = { settingsManager.updateSecondaryCompression(it) }
            )
        }

        item {
            SettingsRadioGroup(
                title = "Source Window Size",
                description = "Amount of source data to keep in memory",
                options = SettingsEntries.SRC_WINDOW_SIZES,
                selectedValue = settingsManager.srcWindowSize,
                onValueChange = { settingsManager.updateSrcWindowSize(it) }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun SettingsRadioGroup(
    title: String,
    description: String,
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            // Current selection display
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = options.find { it.first == selectedValue }?.second ?: "Unknown",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand"
                    )
                }
            }

            if (expanded) {
                AlertDialog(
                    onDismissRequest = { expanded = false },
                    title = { Text(title) },
                    text = {
                        Column(
                            modifier = Modifier.selectableGroup()
                        ) {
                            options.forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = (value == selectedValue),
                                            onClick = {
                                                onValueChange(value)
                                                expanded = false
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (value == selectedValue),
                                        onClick = null
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { expanded = false }) {
                            Text("Done")
                        }
                    }
                )
            }
        }
    }
}

