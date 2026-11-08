package app.mawaqeet.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerTime
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today's Prayer Times and Sunrise, the next Prayer highlighted, and a live countdown to it. */
@Composable
fun HomeScreen(core: PrayerCore, clock: Clock) {
    // Re-query the core every second: the countdown ticks and the display day switches at local midnight.
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(core, clock) {
        while (true) {
            now = clock.instant()
            delay(1000 - now.toEpochMilli() % 1000)
        }
    }
    val day = remember(core, now) { core.displayDay() }
    HomeContent(day, now)
}

@Composable
private fun HomeContent(day: DisplayDay, now: Instant) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.default_location_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NextPrayerCard(day.next, now)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val isNextToday = day.next.time.toLocalDate() == day.date
                day.prayerTimes.forEach { prayerTime ->
                    TimeRow(
                        name = prayerName(prayerTime),
                        time = prayerTime.time,
                        highlighted = isNextToday && prayerTime == day.next,
                    )
                    if (prayerTime.prayer == Prayer.FAJR) {
                        TimeRow(name = stringResource(R.string.sunrise), time = day.sunrise, highlighted = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextPrayerCard(next: PrayerTime, now: Instant) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.bodyLarge, color = onPrimary)
        Text(prayerName(next), style = MaterialTheme.typography.headlineLarge, color = onPrimary)
        Text(formatTime(next.time), style = MaterialTheme.typography.displayMedium, color = onPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.countdown, formatCountdown(Duration.between(now, next.time.toInstant()))),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun TimeRow(name: String, time: ZonedDateTime, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.primaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val weight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = weight,
            color = if (highlighted) colors.primary else colors.onSurface,
        )
        Text(
            text = formatTime(time),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = weight,
            color = if (highlighted) colors.primary else colors.onSurface,
        )
    }
}

@Composable
private fun prayerName(prayerTime: PrayerTime): String = stringResource(
    when {
        prayerTime.isJumuah -> R.string.prayer_jumuah
        else -> when (prayerTime.prayer) {
            Prayer.FAJR -> R.string.prayer_fajr
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        }
    },
)

// Placeholder formatting with Western digits; locale-aware digits and 12/24-hour style belong to #17.
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private fun formatTime(time: ZonedDateTime): String = timeFormatter.format(time)

/** Remaining time as H:MM:SS, always with seconds. */
internal fun formatCountdown(remaining: Duration): String {
    val seconds = remaining.seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}
