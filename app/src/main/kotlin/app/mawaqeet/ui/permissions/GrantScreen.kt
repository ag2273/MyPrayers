package app.mawaqeet.ui.permissions

import androidx.compose.foundation.layout.heightIn
import app.mawaqeet.ui.components.Card
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.alerts.AlertPermissions

/**
 * The blocking "Grant" screen: shown instead of Home while either alert permission is missing. Each missing
 * permission gets its short explanation and a Grant button (system dialog or the right settings page).
 */
@Composable
fun GrantScreen(
    permissions: AlertPermissions,
    onGrantNotifications: () -> Unit,
    onGrantExactAlarms: () -> Unit,
) {
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.grant_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.grant_intro), style = MaterialTheme.typography.bodyLarge)
            if (!permissions.notifications) {
                PermissionStep(R.string.grant_notifications_title, R.string.grant_notifications_body, onGrantNotifications)
            }
            if (!permissions.exactAlarms) {
                PermissionStep(R.string.grant_exact_alarms_title, R.string.grant_exact_alarms_body, onGrantExactAlarms)
            }
        }
    }
}

@Composable
private fun PermissionStep(@StringRes title: Int, @StringRes body: Int, onGrant: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.grant_button)) }
        }
    }
}
