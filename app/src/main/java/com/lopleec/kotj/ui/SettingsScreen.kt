@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lopleec.kotj.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lopleec.kotj.R
import com.lopleec.kotj.data.AppSettings
import com.lopleec.kotj.data.ThemeMode
import com.lopleec.kotj.data.NoteSort

private enum class SettingsChoice { THEME, TRASH, SORT }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1.0"
    }
    val systemUnlockAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    var choice by remember { mutableStateOf<SettingsChoice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_section_appearance)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme)) },
                    supportingContent = { Text(settings.themeMode.label()) },
                    leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
                    modifier = Modifier.clickable { choice = SettingsChoice.THEME },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                    leadingContent = { Icon(Icons.Outlined.AutoAwesome, null) },
                    trailingContent = {
                        Switch(
                            checked = settings.useDynamicColor,
                            onCheckedChange = { onUpdate(settings.copy(useDynamicColor = it)) },
                        )
                    },
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_section_security)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_system_unlock)) },
                    supportingContent = {
                        Text(
                            if (systemUnlockAvailable) {
                                stringResource(R.string.settings_system_unlock_summary)
                            } else {
                                stringResource(R.string.settings_system_unlock_unavailable)
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Fingerprint, null) },
                    trailingContent = {
                        Switch(
                            checked = settings.useSystemUnlock && systemUnlockAvailable,
                            onCheckedChange = { onUpdate(settings.copy(useSystemUnlock = it)) },
                            enabled = systemUnlockAvailable,
                        )
                    },
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_section_notes)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_note_sorting)) },
                    supportingContent = { Text(settings.noteSort.label()) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                    modifier = Modifier.clickable { choice = SettingsChoice.SORT },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_group_by_date)) },
                    supportingContent = { Text(stringResource(R.string.settings_group_by_date_summary)) },
                    leadingContent = { Icon(Icons.Outlined.ViewAgenda, null) },
                    trailingContent = {
                        Switch(
                            checked = settings.groupNotesByDate,
                            onCheckedChange = { onUpdate(settings.copy(groupNotesByDate = it)) },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_confirm_before_deleting)) },
                    supportingContent = { Text(stringResource(R.string.settings_confirm_before_deleting_summary)) },
                    leadingContent = { Icon(Icons.Outlined.WarningAmber, null) },
                    trailingContent = {
                        Switch(
                            checked = settings.confirmBeforeDelete,
                            onCheckedChange = { onUpdate(settings.copy(confirmBeforeDelete = it)) },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_trash_retention)) },
                    supportingContent = { Text(retentionLabel(settings.trashRetentionDays)) },
                    leadingContent = { Icon(Icons.Outlined.DeleteSweep, null) },
                    modifier = Modifier.clickable { choice = SettingsChoice.TRASH },
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text("Kotj") },
                    supportingContent = {
                        Text(stringResource(R.string.settings_about_summary, versionName))
                    },
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                )
            }
        }
    }

    when (choice) {
        SettingsChoice.THEME -> ChoiceSheet(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to it.label() },
            selected = settings.themeMode,
            onDismiss = { choice = null },
            onSelect = { onUpdate(settings.copy(themeMode = it)); choice = null },
        )
        SettingsChoice.TRASH -> ChoiceSheet(
            title = stringResource(R.string.settings_trash_retention),
            options = listOf(7, 30, 90, 0).map { it to retentionLabel(it) },
            selected = settings.trashRetentionDays,
            onDismiss = { choice = null },
            onSelect = { onUpdate(settings.copy(trashRetentionDays = it)); choice = null },
        )
        SettingsChoice.SORT -> ChoiceSheet(
            title = stringResource(R.string.settings_note_sorting),
            options = NoteSort.entries.map { it to it.label() },
            selected = settings.noteSort,
            onDismiss = { choice = null },
            onSelect = { onUpdate(settings.copy(noteSort = it)); choice = null },
        )
        null -> Unit
    }
}

@Composable
private fun NoteSort.label(): String = when (this) {
    NoteSort.UPDATED -> stringResource(R.string.settings_sort_by_date)
    NoteSort.TITLE -> stringResource(R.string.settings_sort_alphabetically)
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp, 12.dp))
        options.forEach { (value, label) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = value == selected, onClick = null) },
                modifier = Modifier.clickable { onSelect(value) },
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun retentionLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.settings_retention_7_days)
    30 -> stringResource(R.string.settings_retention_30_days)
    90 -> stringResource(R.string.settings_retention_90_days)
    else -> stringResource(R.string.settings_retention_forever)
}
