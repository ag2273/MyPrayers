package app.mawaqeet.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.mawaqeet.R

/** The three top-level destinations, in the bottom bar's order (start to end). */
enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_today),
    LOG(R.string.prayer_log, R.drawable.ic_log),
    SETTINGS(R.string.settings, R.drawable.ic_settings),
}

/**
 * The app's frame on a top-level page: [content] for the [selected] tab above the bottom navigation bar. Back on
 * another tab returns to Today first, like the system's own apps.
 */
@Composable
fun MainScaffold(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = selected != Tab.TODAY) { onSelect(Tab.TODAY) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
