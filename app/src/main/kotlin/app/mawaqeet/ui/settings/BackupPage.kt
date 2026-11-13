package app.mawaqeet.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.Backup
import app.mawaqeet.data.RestoreOutcome
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Backup File's media type, for the create-document picker. */
internal const val BACKUP_MIME_TYPE = "application/json"

/**
 * What the open-document picker offers for a restore: some file providers (Drive, messaging apps) label a `.json`
 * file as plain text or bytes.
 */
private val RESTORE_MIME_TYPES = arrayOf(BACKUP_MIME_TYPE, "text/plain", "application/octet-stream")

/** The message after a restore, saved across a recreation (a restored language recreates the screen). */
private enum class RestoreMessage(@StringRes val text: Int, val isError: Boolean = true) {
    RESTORED(R.string.backup_restored, isError = false),
    RESTORED_CHECK_ALERTS(R.string.backup_restored, isError = false),
    DAMAGED(R.string.backup_restore_damaged),
    NEWER_VERSION(R.string.backup_restore_newer),
    UNREADABLE(R.string.backup_restore_unreadable),
}

private fun RestoreOutcome.message() = when (this) {
    is RestoreOutcome.Restored -> if (usesAlertPermissions) RestoreMessage.RESTORED_CHECK_ALERTS else RestoreMessage.RESTORED
    RestoreOutcome.Damaged -> RestoreMessage.DAMAGED
    RestoreOutcome.NewerVersion -> RestoreMessage.NEWER_VERSION
    RestoreOutcome.Unreadable -> RestoreMessage.UNREADABLE
}

/**
 * Settings → Backup (#98): what the Backup File holds, the privacy warning, the last export date, and export
 * through the system's create-document picker (no permission, nothing sent anywhere); restore (#99) through the
 * open-document picker, after a confirmation that the file's settings replace the current ones.
 */
@Composable
internal fun ColumnScope.BackupPage(lastExport: Instant?, actions: SettingsActions) {
    var failed by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        // A cancelled picker gives null: nothing is written and nothing changes.
        if (uri != null) actions.exportBackup(uri) { ok -> failed = !ok }
    }
    // The picked file waits here until the user confirms; nothing is read before.
    var toRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restoreMessage by rememberSaveable { mutableStateOf<RestoreMessage?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> toRestore = uri }
    toRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { toRestore = null },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    toRestore = null
                    actions.restoreBackup(uri) { restoreMessage = it.message() }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { toRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val format = LocalDisplayFormatter.current

    SectionTitle(stringResource(R.string.backup_contents_section))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.backup_contents_body), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.backup_contents_not_included),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionTitle(stringResource(R.string.about_privacy_section))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.backup_privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.backup_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionTitle(stringResource(R.string.backup_file_section))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (lastExport == null) {
                    stringResource(R.string.backup_never_exported)
                } else {
                    stringResource(R.string.backup_last_export, format.gregorianDate(lastExport.atZone(ZoneId.systemDefault()).toLocalDate()))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (failed) {
                Text(stringResource(R.string.backup_export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    failed = false
                    restoreMessage = null
                    picker.launch(Backup.fileName(LocalDate.now()))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(
                onClick = {
                    failed = false
                    restoreMessage = null
                    restorePicker.launch(RESTORE_MIME_TYPES)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
            restoreMessage?.let { message ->
                Text(
                    stringResource(message.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (message == RestoreMessage.RESTORED_CHECK_ALERTS) {
                    Text(
                        stringResource(R.string.backup_restored_check_alerts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = actions::openFixAlerts, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.fix_alerts))
                    }
                }
            }
        }
    }
}
