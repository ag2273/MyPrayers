package app.mawaqeet.ui.adhkar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mawaqeet.R
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.theme.LocalSkin
import app.mawaqeet.ui.theme.NotoKufiArabic

/** Settings → Language and display → Tasbeeh (1.4): the hand that counts. */
enum class TasbeehHand { RIGHT, LEFT }

/**
 * The free Tasbeeh (#71) in the "A Day That Breathes" design (1.4): the round count button sits on the side of the
 * counting [hand] and today's total on the other side, so the thumb never covers it. Under them, which hand it is set
 * for with a link to change it ([onChangeHand]), and the reset. The button is one TalkBack item that reads the total.
 */
@Composable
internal fun TasbeehPad(total: Int, hand: TasbeehHand, onCount: () -> Unit, onReset: () -> Unit, onChangeHand: () -> Unit) {
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme
    val state = stringResource(R.string.tasbeeh_total, format.number(total))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val button = (maxWidth * 0.42f).coerceIn(136.dp, 176.dp)
            // Laid out left to right whatever the language: the right hand's button is on the physical right.
            val contentDirection = LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val count = @Composable {
                        CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                            Column(
                                Modifier.weight(1f).clearAndSetSemantics {},
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    format.number(total),
                                    fontFamily = NotoKufiArabic,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 60.sp,
                                    lineHeight = 72.sp,
                                    maxLines = 1,
                                )
                                Text(stringResource(R.string.tasbeeh_today), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    val pad = @Composable {
                        CountButton(
                            Modifier.size(button).semantics {
                                stateDescription = state
                                liveRegion = LiveRegionMode.Polite
                            },
                            onCount,
                        )
                    }
                    if (hand == TasbeehHand.RIGHT) {
                        count()
                        pad()
                    } else {
                        pad()
                        count()
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (hand == TasbeehHand.RIGHT) R.string.tasbeeh_set_right else R.string.tasbeeh_set_left),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = onChangeHand) { Text(stringResource(R.string.tasbeeh_hand_change)) }
        }
        OutlinedButton(
            onClick = onReset,
            enabled = total > 0,
            border = BorderStroke(1.dp, LocalSkin.current.line),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.tasbeeh_reset))
        }
    }
}

/** The teal bead: a soft highlight, an amber rim, the Adhkar mark and «اضغط للتسبيح». */
@Composable
private fun CountButton(modifier: Modifier, onCount: () -> Unit) {
    val teal = Color(0xFF1F6F66)
    Box(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = Color(0x59134A44), spotColor = Color(0x59134A44))
            .clip(CircleShape)
            .background(teal)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width * 0.38f, size.height * 0.32f),
                        radius = size.minDimension * 0.62f,
                    ),
                )
            }
            .border(1.dp, Color(0x80E0A246), CircleShape)
            .clickable(onClickLabel = stringResource(R.string.adhkar_count_one), onClick = onCount),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_tasbeeh),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.tasbeeh_tap_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        )
    }
}
