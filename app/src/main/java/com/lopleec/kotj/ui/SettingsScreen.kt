@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lopleec.kotj.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import com.lopleec.kotj.BuildConfig
import org.uwuaosp.compose.settingslib.PreferenceGroupSpacer
import org.uwuaosp.compose.settingslib.PreferencePosition
import org.uwuaosp.compose.settingslib.PreferenceRow
import org.uwuaosp.compose.settingslib.SettingsCategory
import org.uwuaosp.compose.settingslib.SettingsHomepageIcon
import org.uwuaosp.compose.settingslib.SettingsScaffold
import org.uwuaosp.compose.settingslib.SwitchPreferenceRow

private enum class SettingsChoice { THEME, TRASH, SORT }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val systemUnlockAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    var choice by remember { mutableStateOf<SettingsChoice?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        showBackButton = true,
        onNavigateUp = onBack,
    ) {
        SettingsCategory(title = stringResource(R.string.settings_section_appearance))
        PreferenceRow(
            title = stringResource(R.string.settings_theme),
            summary = settings.themeMode.label(),
            iconContent = { SettingsHomepageIcon(Icons.Outlined.DarkMode) },
            position = PreferencePosition.Top,
            onClick = { choice = SettingsChoice.THEME },
        )
        PreferenceGroupSpacer()
        SwitchPreferenceRow(
            title = stringResource(R.string.settings_dynamic_color),
            summary = stringResource(R.string.settings_dynamic_color_summary),
            checked = settings.useDynamicColor,
            onCheckedChange = { onUpdate(settings.copy(useDynamicColor = it)) },
            iconContent = { SettingsHomepageIcon(Icons.Outlined.AutoAwesome) },
            position = PreferencePosition.Bottom,
        )

        SettingsCategory(title = stringResource(R.string.settings_section_security))
        SwitchPreferenceRow(
            title = stringResource(R.string.settings_system_unlock),
            summary = if (systemUnlockAvailable) {
                stringResource(R.string.settings_system_unlock_summary)
            } else {
                stringResource(R.string.settings_system_unlock_unavailable)
            },
            checked = settings.useSystemUnlock && systemUnlockAvailable,
            onCheckedChange = { onUpdate(settings.copy(useSystemUnlock = it)) },
            enabled = systemUnlockAvailable,
            iconContent = { SettingsHomepageIcon(Icons.Outlined.Fingerprint) },
        )

        SettingsCategory(title = stringResource(R.string.settings_section_notes))
        PreferenceRow(
            title = stringResource(R.string.settings_note_sorting),
            summary = settings.noteSort.label(),
            iconContent = { SettingsHomepageIcon(Icons.AutoMirrored.Outlined.Sort) },
            position = PreferencePosition.Top,
            onClick = { choice = SettingsChoice.SORT },
        )
        PreferenceGroupSpacer()
        SwitchPreferenceRow(
            title = stringResource(R.string.settings_group_by_date),
            summary = stringResource(R.string.settings_group_by_date_summary),
            checked = settings.groupNotesByDate,
            onCheckedChange = { onUpdate(settings.copy(groupNotesByDate = it)) },
            iconContent = { SettingsHomepageIcon(Icons.Outlined.ViewAgenda) },
            position = PreferencePosition.Middle,
        )
        PreferenceGroupSpacer()
        SwitchPreferenceRow(
            title = stringResource(R.string.settings_confirm_before_deleting),
            summary = stringResource(R.string.settings_confirm_before_deleting_summary),
            checked = settings.confirmBeforeDelete,
            onCheckedChange = { onUpdate(settings.copy(confirmBeforeDelete = it)) },
            iconContent = { SettingsHomepageIcon(Icons.Outlined.WarningAmber) },
            position = PreferencePosition.Middle,
        )
        PreferenceGroupSpacer()
        PreferenceRow(
            title = stringResource(R.string.settings_trash_retention),
            summary = retentionLabel(settings.trashRetentionDays),
            iconContent = { SettingsHomepageIcon(Icons.Outlined.DeleteSweep) },
            position = PreferencePosition.Bottom,
            onClick = { choice = SettingsChoice.TRASH },
        )

        SettingsCategory(title = stringResource(R.string.settings_section_about))
        PreferenceRow(
            title = stringResource(R.string.settings_about_kotj),
            summary = stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME),
            iconContent = { SettingsHomepageIcon(Icons.Outlined.Info) },
            onClick = {},
        )
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
