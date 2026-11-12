package app.mawaqeet.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.Backup
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Backup File's media type, for the create-document picker. */
internal const val BACKUP_MIME_TYPE = "application/json"

/**
 * Settings → Backup (#98): what the Backup File holds, the privacy warning, the last export date, and export
 * through the system's create-document picker (no permission, nothing sent anywhere). Restore arrives in #99.
 */
@Composable
internal fun ColumnScope.BackupPage(lastExport: Instant?, actions: SettingsActions) {
    var failed by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        // A cancelled picker gives null: nothing is written and nothing changes.
        if (uri != null) actions.exportBackup(uri) { ok -> failed = !ok }
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
                    picker.launch(Backup.fileName(LocalDate.now()))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.backup_restore))
            }
            Text(
                stringResource(R.string.backup_restore_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
