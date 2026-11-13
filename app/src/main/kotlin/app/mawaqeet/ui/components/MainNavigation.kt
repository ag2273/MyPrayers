package app.mawaqeet.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mawaqeet.R
import app.mawaqeet.ui.theme.LocalSkin
import app.mawaqeet.ui.theme.NotoKufiArabic

/** The top-level destinations, in the bottom bar's order (start to end). */
enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_home),
    LOG(R.string.tab_log, R.drawable.ic_tab_log),
    ADHKAR(R.string.tab_adhkar, R.drawable.ic_tasbeeh),
    SETTINGS(R.string.settings, R.drawable.ic_gear),
}

/**
 * What the top bar shows besides the brand: the location chip (opens the city search) and the two quick buttons, the
 * other language and light/dark. Each is the same setting as in Settings → Language and display.
 */
data class TopBar(
    val locationName: String,
    val onOpenLocation: () -> Unit,
    /** True when the app is shown in Arabic: the language button then offers English. */
    val arabic: Boolean,
    val onToggleLanguage: () -> Unit,
    /** True when the app is dark now: the theme button then offers light. */
    val dark: Boolean,
    val onToggleTheme: () -> Unit,
)

/**
 * The app's frame on a top-level page ("A Day That Breathes"): the top bar ([topBar], none in tests that leave it
 * out), [content] for the [selected] tab, and the floating tab bar. Everything is transparent over the living sky. Back
 * on another tab returns to Today first, like the system's own apps.
 */
@Composable
fun MainScaffold(selected: Tab, onSelect: (Tab) -> Unit, topBar: TopBar? = null, content: @Composable () -> Unit) {
    BackHandler(enabled = selected != Tab.TODAY) { onSelect(Tab.TODAY) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { if (topBar != null) MainTopBar(topBar) },
        bottomBar = { FloatingTabBar(selected, onSelect) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

private val BarShape = RoundedCornerShape(22.dp)

/** The four tabs on a rounded bar that floats above the gesture area, the selected one on a soft teal pill. */
@Composable
private fun FloatingTabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp)
            .shadow(18.dp, BarShape, ambientColor = Color(0x29134A44), spotColor = Color(0x29134A44))
            .clip(BarShape)
            .background(skin.bar)
            .border(1.dp, skin.line, BarShape)
            .padding(5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) skin.accentSoft else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 2.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    painterResource(tab.icon),
                    contentDescription = null,
                    tint = if (on) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** «مواقيت» with its Latin name under it, then the location chip and the language and theme buttons at the end. */
@Composable
private fun MainTopBar(bar: TopBar) {
    val colors = MaterialTheme.colorScheme
    // At the largest text sizes the city name no longer fits beside the brand: the chip takes a line of its own.
    val chipBelow = LocalDensity.current.fontScale >= 1.5f
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
    ) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.clearAndSetSemantics {}) {
            Text(
                stringResource(R.string.app_name),
                fontFamily = NotoKufiArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            )
            if (bar.arabic) {
                Text(
                    "MAWAQEET",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.6.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        // The chip takes the room between the brand and the buttons, at the end of it.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (!chipBelow) LocationChip(bar.locationName, bar.onOpenLocation)
        }
        SquareButton(
            description = stringResource(if (bar.arabic) R.string.language_english else R.string.language_arabic),
            onClick = bar.onToggleLanguage,
        ) {
            Text(
                if (bar.arabic) "EN" else "ع",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 0.6.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        SquareButton(
            description = stringResource(if (bar.dark) R.string.theme_light else R.string.theme_dark),
            onClick = bar.onToggleTheme,
        ) {
            Icon(painterResource(if (bar.dark) R.drawable.ic_sun else R.drawable.ic_moon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
    if (chipBelow) LocationChip(bar.locationName, bar.onOpenLocation, Modifier.align(Alignment.End))
    }
}

/** The current location as a tappable chip with a pin: it opens the city search. */
@Composable
private fun LocationChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .widthIn(max = if (LocalDensity.current.fontScale >= 1.5f) 320.dp else 180.dp)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(skin.surface)
            .border(1.dp, skin.line, shape)
            .clickable(onClickLabel = stringResource(R.string.location), onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A 44dp rounded-square button on the translucent surface (in a 48dp touch target). */
@Composable
private fun SquareButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .padding(2.dp)
            .clip(shape)
            .background(skin.surface)
            .border(1.dp, skin.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
