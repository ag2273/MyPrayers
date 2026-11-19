package app.mawaqeet.ui.adhkar

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.R
import app.mawaqeet.audio.AdhkarAudio
import app.mawaqeet.audio.AudioPackState
import app.mawaqeet.audio.PlayerState
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.RowText
import app.mawaqeet.ui.components.SwitchRow
import app.mawaqeet.ui.theme.LocalSkin

/** This process' Adhkar Audio, or null when the build carries no pack key (then no audio UI shows anywhere). */
@Composable
fun rememberAdhkarAudio(): AdhkarAudio? {
    val context = LocalContext.current
    return remember { (context.applicationContext as? MawaqeetApplication)?.adhkarAudio }
}

/** [bytes] as megabytes with one decimal, in the chosen digits: «٣٫٨». */
fun DisplayFormatter.megabytes(bytes: Long): String {
    val tenths = ((bytes + 50_000) / 100_000).toInt()
    val separator = if (number(0) == "0") "." else "٫"
    return number(tenths / 10) + separator + number(tenths % 10)
}

/** A playing time as m:ss in the chosen digits. */
private fun DisplayFormatter.minutesSeconds(ms: Int): String {
    val s = ms / 1000
    return number(s / 60) + ":" + number((s % 60) / 10) + number(s % 10)
}

private enum class Network { WIFI, MOBILE, NONE }

private fun network(context: Context): Network {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return Network.NONE
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return Network.NONE
    return when {
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Network.NONE
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> Network.WIFI
        else -> Network.MOBILE
    }
}

/** A soft teal tile with an icon, as on the redesign's audio card. */
@Composable
private fun AudioTile(@DrawableRes icon: Int) {
    Box(Modifier.size(40.dp).background(LocalSkin.current.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * The reader's card about the one-time download, until the audio is all on the phone: the offer (hidden once the
 * user said «ليس الآن», though the play button still offers it), then the download's progress or why it waits.
 */
@Composable
fun AudioDownloadCard(audio: AdhkarAudio, onOffer: () -> Unit) {
    val state by audio.store.state.collectAsState()
    val prefs by audio.prefs.collectAsState()
    if (state.complete) return
    val idle = state.phase == AudioPackState.Phase.IDLE
    if (idle && prefs.offerDismissed && !state.started) return
    val format = LocalDisplayFormatter.current
    Card {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.phase) {
                AudioPackState.Phase.IDLE -> {
                    AudioTile(R.drawable.ic_headphones)
                    RowText(
                        stringResource(R.string.adhkar_audio_offer_title),
                        if (state.started) {
                            stringResource(R.string.adhkar_audio_offer_partial, format.number(state.ready), format.number(state.total))
                        } else {
                            stringResource(R.string.adhkar_audio_offer_body, format.megabytes(state.totalBytes))
                        },
                    )
                    if (state.started) {
                        Button(onClick = audio::startDownload, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.adhkar_audio_continue)) }
                    } else {
                        Button(onClick = onOffer, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.adhkar_audio_download)) }
                    }
                }
                AudioPackState.Phase.WAITING, AudioPackState.Phase.DOWNLOADING -> {
                    AudioTile(R.drawable.ic_download)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (state.phase == AudioPackState.Phase.DOWNLOADING) {
                            val percent = (state.doneBytes * 100 / state.totalBytes.coerceAtLeast(1)).toInt()
                            Text(stringResource(R.string.adhkar_audio_downloading, format.number(percent)), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.adhkar_audio_downloading_detail,
                                    format.number(state.ready),
                                    format.number(state.total),
                                    format.megabytes(state.doneBytes),
                                    format.megabytes(state.totalBytes),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(if (prefs.mobileData) R.string.adhkar_audio_waiting else R.string.adhkar_audio_waiting_wifi),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.adhkar_audio_waiting_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { state.doneBytes.toFloat() / state.totalBytes.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            drawStopIndicator = {},
                        )
                    }
                    OutlinedButton(onClick = audio::stopDownload, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.adhkar_audio_stop)) }
                }
                AudioPackState.Phase.FAILED -> {
                    AudioTile(R.drawable.ic_warning)
                    RowText(stringResource(R.string.adhkar_audio_failed), stringResource(R.string.adhkar_audio_failed_detail))
                    Button(onClick = audio::startDownload, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.adhkar_audio_retry)) }
                }
            }
        }
    }
}

/** The bottom sheet that explains the one-time download before it starts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDownloadSheet(audio: AdhkarAudio, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val format = LocalDisplayFormatter.current
    val prefs by audio.prefs.collectAsState()
    val network = remember { network(context) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(52.dp).background(LocalSkin.current.accentSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_headphones), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Text(stringResource(R.string.adhkar_audio_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.adhkar_audio_sheet_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(R.drawable.ic_download, stringResource(R.string.adhkar_audio_sheet_size, format.megabytes(audio.totalBytes)))
                Fact(
                    R.drawable.ic_wifi,
                    stringResource(
                        when {
                            network == Network.NONE -> R.string.adhkar_audio_sheet_offline
                            network == Network.WIFI -> R.string.adhkar_audio_sheet_wifi
                            prefs.mobileData -> R.string.adhkar_audio_sheet_mobile
                            else -> R.string.adhkar_audio_sheet_mobile_off
                        },
                    ),
                )
                Fact(R.drawable.ic_trash, stringResource(R.string.adhkar_audio_sheet_delete))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        audio.updatePrefs { it.copy(offerDismissed = true) }
                        onDismiss()
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.adhkar_audio_not_now)) }
                Button(
                    onClick = {
                        audio.startDownload()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.adhkar_audio_download)) }
            }
        }
    }
}

@Composable
private fun Fact(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Inside the dhikr card, under its text: play or pause this dhikr's [recording] with its progress, or, before the
 * download, a button that offers it. Taps here never count the dhikr (the rest of the card does).
 */
@Composable
fun DhikrPlayer(audio: AdhkarAudio, recording: String, onOffer: () -> Unit) {
    val pack by audio.store.state.collectAsState()
    val player by audio.player.state.collectAsState()
    val format = LocalDisplayFormatter.current
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    val ready = remember(pack) { audio.store.isReady(recording) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(skin.surfaceStrong, RoundedCornerShape(16.dp))
            .border(1.dp, skin.line, RoundedCornerShape(16.dp))
            // Swallows taps between the controls, so they don't reach the card and count.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ready) {
            val mine: PlayerState? = player.takeIf { it.recording == recording }
            val playing = mine?.playing == true
            IconButton(
                onClick = { if (playing) audio.player.pause() else audio.player.play(recording) },
                modifier = Modifier.size(48.dp).background(colors.primary, CircleShape),
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(if (playing) R.string.adhkar_audio_pause else R.string.adhkar_audio_play),
                    tint = colors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                val duration = mine?.durationMs ?: 0
                val position = mine?.positionMs ?: 0
                LinearProgressIndicator(
                    progress = { if (duration > 0) position.toFloat() / duration else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Times read left to right in either language.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(format.minutesSeconds(position), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Text(if (duration > 0) format.minutesSeconds(duration) else "", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            val downloading = pack.phase == AudioPackState.Phase.DOWNLOADING || pack.phase == AudioPackState.Phase.WAITING
            Box {
                IconButton(
                    onClick = onOffer,
                    enabled = !downloading,
                    modifier = Modifier.size(48.dp).background(skin.accentSoft, CircleShape),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_headphones),
                        contentDescription = stringResource(R.string.adhkar_audio_listen),
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(18.dp).background(skin.amber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(11.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.adhkar_audio_listen), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (downloading) R.string.adhkar_audio_listen_downloading else R.string.adhkar_audio_listen_needs_download),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Under «السابق» and «التالي»: whether a finished recording moves on to the next dhikr and plays it. */
@Composable
fun AutoNextRow(audio: AdhkarAudio) {
    val pack by audio.store.state.collectAsState()
    val prefs by audio.prefs.collectAsState()
    if (!pack.started) return
    Card {
        SwitchRow(
            stringResource(R.string.adhkar_audio_auto_next),
            stringResource(R.string.adhkar_audio_auto_next_summary),
            prefs.autoNext,
        ) { on -> audio.updatePrefs { it.copy(autoNext = on) } }
    }
}

/** Settings → Adhkar audio: what is on the phone, the listening and download choices, and deleting it. */
@Composable
fun AdhkarAudioPage(audio: AdhkarAudio) {
    val state by audio.store.state.collectAsState()
    val prefs by audio.prefs.collectAsState()
    val format = LocalDisplayFormatter.current
    var sheet by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (sheet) AudioDownloadSheet(audio) { sheet = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.adhkar_audio_delete_title)) },
            text = { Text(stringResource(R.string.adhkar_audio_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    audio.delete()
                }) { Text(stringResource(R.string.adhkar_audio_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (!state.complete) AudioDownloadCard(audio) { sheet = true }
    Card {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AudioTile(if (state.complete) R.drawable.ic_check else R.drawable.ic_headphones)
            if (state.complete) {
                RowText(
                    stringResource(R.string.adhkar_audio_ready),
                    stringResource(R.string.adhkar_audio_ready_summary, format.number(state.total), format.megabytes(state.totalBytes)),
                )
            } else {
                RowText(
                    stringResource(R.string.adhkar_audio_not_downloaded),
                    stringResource(R.string.adhkar_audio_not_downloaded_summary, format.megabytes(state.totalBytes)),
                )
            }
        }
        RowDivider()
        SwitchRow(
            stringResource(R.string.adhkar_audio_auto_next),
            stringResource(R.string.adhkar_audio_auto_next_summary),
            prefs.autoNext,
        ) { on -> audio.updatePrefs { it.copy(autoNext = on) } }
        RowDivider()
        SwitchRow(
            stringResource(R.string.adhkar_audio_mobile_data),
            stringResource(R.string.adhkar_audio_mobile_data_summary),
            prefs.mobileData,
        ) { on -> audio.updatePrefs { it.copy(mobileData = on) } }
    }
    Text(
        stringResource(R.string.adhkar_audio_protected),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
    if (state.started || state.phase != AudioPackState.Phase.IDLE) {
        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.adhkar_audio_delete), color = MaterialTheme.colorScheme.error) }
        Text(
            stringResource(R.string.adhkar_audio_delete_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
