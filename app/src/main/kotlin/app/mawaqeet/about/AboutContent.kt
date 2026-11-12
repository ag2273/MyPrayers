package app.mawaqeet.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.mawaqeet.BuildConfig
import app.mawaqeet.R

/**
 * About (#33): the version, one line on what the app does, and every library, dataset, font and recording it is
 * built from with its licence. Each credit opens its website. Content only, so it can sit in any screen or group.
 */
@Composable
fun ColumnScope.AboutContent() {
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyMedium)
    for (kind in CreditKind.entries) {
        Text(
            stringResource(kind.title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Credits.of(kind).forEach { CreditRow(it) }
    }
}

@Composable
private fun CreditRow(credit: Credit) {
    val uri = LocalUriHandler.current
    val use = stringResource(credit.use)
    val name = credit.nameRes?.let { stringResource(it) } ?: credit.name
    val licence = credit.licenceRes?.let { stringResource(it) } ?: credit.licence
    val spoken = stringResource(R.string.about_credit_description, "$name: $use", licence)
    val open = { runCatching { uri.openUri(credit.url) }; Unit }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = open)
            // One clear sentence for TalkBack instead of three fragments.
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick { open(); true }
            }
            .padding(vertical = 6.dp),
    ) {
        // Latin names in an Arabic page still line up at the page's start, not at their own (left) one.
        val align = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
        val full = Modifier.fillMaxWidth()
        Text(name, style = MaterialTheme.typography.bodyLarge, textAlign = align, modifier = full)
        Text(use, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = full)
        Text(
            stringResource(R.string.about_licence, licence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = full,
        )
    }
}
