package com.jaafer.tvremote.ui

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

/**
 * Fires on touch-down (not on release) for an instant feel, with haptics.
 * When [repeat] is set, [onDown] repeats while the finger stays down.
 */
@Composable
fun Modifier.pressable(
    interaction: MutableInteractionSource,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
    repeat: Boolean = false,
): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    return pointerInput(repeat) {
        detectTapGestures(onPress = { offset ->
            val press = PressInteraction.Press(offset)
            interaction.tryEmit(press)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            down()
            val repeater = if (repeat) scope.launch {
                delay(400)
                while (isActive) {
                    down()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    delay(90)
                }
            } else null
            tryAwaitRelease()
            repeater?.cancel()
            up()
            interaction.tryEmit(PressInteraction.Release(press))
        })
    }
}

@Composable
fun RoundKey(
    icon: ImageVector,
    label: String,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    onUp: () -> Unit = {},
    size: Dp = 64.dp,
    tint: Color = RC.text,
    highlighted: Boolean = false,
    repeat: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "scale")
    val bg by animateColorAsState(
        when {
            pressed -> RC.outline
            highlighted -> RC.accent.copy(alpha = 0.25f)
            else -> RC.surface
        },
        label = "bg",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .pressable(interaction, onDown, onUp, repeat)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (highlighted) RC.accent else tint, modifier = Modifier.size(size * 0.42f))
    }
}

/** Vertical rocker such as volume or channel. */
@Composable
fun Rocker(
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    onUp: () -> Unit,
    onDownKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(68.dp).clip(RoundedCornerShape(34.dp)).background(RC.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RockerHalf(upIcon, "$label +", onUp)
        Text(label, color = RC.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        RockerHalf(downIcon, "$label -", onDownKey)
    }
}

@Composable
private fun RockerHalf(icon: ImageVector, label: String, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) RC.outline else Color.Transparent, label = "bg")
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(interaction, onPress, repeat = true)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = RC.text, modifier = Modifier.size(26.dp))
    }
}

/** Circular D-pad: the whole ring is touchable, direction is chosen by angle. */
@Composable
fun DPad(
    onDirection: (Int) -> Unit,
    onOkDown: () -> Unit,
    onOkUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val direction by rememberUpdatedState(onDirection)
    val okDown by rememberUpdatedState(onOkDown)
    val okUp by rememberUpdatedState(onOkUp)
    var pressed by remember { mutableIntStateOf(0) }
    val highlight = RC.accent.copy(alpha = 0.16f)

    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(RC.surfaceHi, RC.surface)))
            .border(1.dp, RC.outline.copy(alpha = 0.6f), CircleShape)
            .drawBehind {
                val start = when (pressed) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> -45f
                    KeyEvent.KEYCODE_DPAD_DOWN -> 45f
                    KeyEvent.KEYCODE_DPAD_LEFT -> 135f
                    KeyEvent.KEYCODE_DPAD_UP -> 225f
                    else -> null
                }
                if (start != null) drawArc(highlight, start, 90f, useCenter = true)
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pos ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val d = pos - center
                    val radius = size.width / 2f
                    val code = if (d.getDistance() < radius * CENTER_RATIO) {
                        KeyEvent.KEYCODE_DPAD_CENTER
                    } else {
                        val angle = Math.toDegrees(atan2(d.y, d.x).toDouble())
                        when {
                            angle >= -45 && angle < 45 -> KeyEvent.KEYCODE_DPAD_RIGHT
                            angle >= 45 && angle < 135 -> KeyEvent.KEYCODE_DPAD_DOWN
                            angle >= -135 && angle < -45 -> KeyEvent.KEYCODE_DPAD_UP
                            else -> KeyEvent.KEYCODE_DPAD_LEFT
                        }
                    }
                    pressed = code
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER) {
                        okDown()
                        tryAwaitRelease()
                        okUp()
                    } else {
                        direction(code)
                        val repeater = scope.launch {
                            delay(380)
                            while (isActive) {
                                direction(code)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                delay(85)
                            }
                        }
                        tryAwaitRelease()
                        repeater.cancel()
                    }
                    pressed = 0
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val arrow = Modifier.size(38.dp)
        Icon(Icons.Rounded.KeyboardArrowUp, "أعلى", Modifier.align(Alignment.TopCenter).padding(top = 16.dp).then(arrow), tint = RC.text)
        Icon(Icons.Rounded.KeyboardArrowDown, "أسفل", Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).then(arrow), tint = RC.text)
        Icon(Icons.Rounded.KeyboardArrowLeft, "يسار", Modifier.align(Alignment.CenterStart).padding(start = 16.dp).then(arrow), tint = RC.text)
        Icon(Icons.Rounded.KeyboardArrowRight, "يمين", Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).then(arrow), tint = RC.text)
        val okBg by animateColorAsState(
            if (pressed == KeyEvent.KEYCODE_DPAD_CENTER) RC.accent.copy(alpha = 0.35f) else RC.surfaceHi,
            label = "ok",
        )
        Box(
            Modifier
                .fillMaxSize(CENTER_RATIO * 2 - 0.04f)
                .clip(CircleShape)
                .background(okBg)
                .border(1.dp, RC.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = RC.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val CENTER_RATIO = 0.36f

/** Swipe to move focus, tap to select – like the touchpad of the official app. */
@Composable
fun Touchpad(
    onDirection: (Int) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val direction by rememberUpdatedState(onDirection)
    val ok by rememberUpdatedState(onOk)
    Box(
        modifier
            .clip(RoundedCornerShape(40.dp))
            .background(Brush.verticalGradient(listOf(RC.surfaceHi, RC.surface)))
            .border(1.dp, RC.outline.copy(alpha = 0.6f), RoundedCornerShape(40.dp))
            .pointerInput(Unit) {
                val step = 40.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var acc = Offset.Zero
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        acc += change.positionChange()
                        change.consume()
                        while (abs(acc.x) >= step || abs(acc.y) >= step) {
                            moved = true
                            if (abs(acc.x) >= abs(acc.y)) {
                                direction(if (acc.x > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                                acc = Offset(acc.x - sign(acc.x) * step, 0f)
                            } else {
                                direction(if (acc.y > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                                acc = Offset(0f, acc.y - sign(acc.y) * step)
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } while (change.pressed)
                    if (!moved) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ok()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("اسحب للتنقل • انقر للاختيار", color = RC.muted.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

@Composable
fun MicButton(listening: Boolean, onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "scale")
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "p")
            Box(
                Modifier
                    .size(80.dp)
                    .graphicsLayer { scaleX = 1f + p * 0.32f; scaleY = 1f + p * 0.32f; alpha = 1f - p }
                    .background(RC.accent.copy(alpha = 0.5f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(80.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RC.accent, RC.accentDeep)))
                .pressable(interaction, onPress, onRelease)
                .semantics { contentDescription = "المساعد الصوتي"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, null, tint = RC.onAccent, modifier = Modifier.size(36.dp))
        }
    }
}
