package app.mawaqeet.ui.fixalerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.SubPage
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.AlarmKillingBrand

/**
 * "Fix alerts" (#29): what to change in the phone's own battery settings so it stops delaying or killing Adhan
 * Alerts, with buttons that only OPEN system settings pages. The app never requests a battery-optimisation
 * exemption itself (Play policy): neither the exemption permission nor its request intent is ever used.
 */
object FixAlerts {
    /** This phone's alarm-killing brand, or null. */
    val deviceBrand: AlarmKillingBrand? get() = AlarmKillingBrand.of(Build.MANUFACTURER ?: "")

    /** The app's own page in system settings (battery, autostart and background options hang off it). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** The system list of apps and their battery optimisation, where the user changes it themselves. */
    fun batteryOptimisationListIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Opens [intent], falling back to the app's settings page on phones that lack it. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(appSettingsIntent(context))
        }
    }

    /** The numbered steps for [brand] (general steps for other phones). */
    @StringRes
    fun steps(brand: AlarmKillingBrand?): List<Int> = when (brand) {
        AlarmKillingBrand.SAMSUNG -> listOf(R.string.fix_samsung_1, R.string.fix_samsung_2, R.string.fix_samsung_3)
        AlarmKillingBrand.XIAOMI -> listOf(R.string.fix_xiaomi_1, R.string.fix_xiaomi_2, R.string.fix_xiaomi_3)
        AlarmKillingBrand.HUAWEI -> listOf(R.string.fix_huawei_1, R.string.fix_huawei_2)
        AlarmKillingBrand.OPPO -> listOf(R.string.fix_oppo_1, R.string.fix_oppo_2, R.string.fix_oppo_3)
        AlarmKillingBrand.VIVO -> listOf(R.string.fix_vivo_1, R.string.fix_vivo_2)
        null -> listOf(R.string.fix_general_1, R.string.fix_general_2)
    }

    @StringRes
    fun brandName(brand: AlarmKillingBrand?): Int = when (brand) {
        AlarmKillingBrand.SAMSUNG -> R.string.brand_samsung
        AlarmKillingBrand.XIAOMI -> R.string.brand_xiaomi
        AlarmKillingBrand.HUAWEI -> R.string.brand_huawei
        AlarmKillingBrand.OPPO -> R.string.brand_oppo
        AlarmKillingBrand.VIVO -> R.string.brand_vivo
        null -> R.string.brand_other
    }
}

/** The instructions and settings buttons, shared by the Fix alerts screen and onboarding's battery step. */
@Composable
fun ColumnScope.FixAlertsContent(brand: AlarmKillingBrand?) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(colors.secondaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = colors.onSecondaryContainer)
        }
        Text(
            stringResource(R.string.fix_alerts_intro, stringResource(FixAlerts.brandName(brand))),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
    FixAlerts.steps(brand).forEachIndexed { i, step ->
        Card {
            Row(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(30.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text(LocalDisplayFormatter.current.number(i + 1), style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                }
                Text(stringResource(step), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
    OutlinedButton(onClick = { FixAlerts.open(context, FixAlerts.appSettingsIntent(context)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.fix_open_app_settings))
    }
    OutlinedButton(onClick = { FixAlerts.open(context, FixAlerts.batteryOptimisationListIntent()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.fix_open_battery_settings))
    }
}

/** Settings → Help → Fix alerts. "Done" also hides the Home tip. */
@Composable
fun FixAlertsScreen(onDone: () -> Unit, onBack: () -> Unit) {
    SubPage(stringResource(R.string.fix_alerts), onBack = onBack) {
        FixAlertsContent(FixAlerts.deviceBrand)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.done)) }
    }
}
