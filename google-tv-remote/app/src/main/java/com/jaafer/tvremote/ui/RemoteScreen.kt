package com.jaafer.tvremote.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaafer.tvremote.ConnState
import com.jaafer.tvremote.RemoteViewModel

@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val touchpad by vm.touchpad.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val spoken by vm.spokenText.collectAsStateWithLifecycle()
    val textField by vm.textFieldActive.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    var showKeyboard by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.messages.tryEmit("يلزم إذن الميكروفون لاستخدام المساعد الصوتي")
    }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar(
                name = device?.name ?: "",
                state = state,
                touchpad = touchpad,
                onDevices = vm::showDevices,
                onApps = { showApps = true },
                onToggleMode = vm::toggleTouchpad,
                onPowerDown = { vm.keyDown(KeyEvent.KEYCODE_POWER) },
                onPowerUp = { vm.keyUp(KeyEvent.KEYCODE_POWER) },
            )
            if (state is ConnState.Failed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RC.danger.copy(alpha = 0.12f))
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((state as ConnState.Failed).message, color = RC.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::retry) { Text("إعادة المحاولة") }
                }
            }

            // The pad always keeps physical directions, even in a right-to-left UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = min(min(maxWidth * 0.86f, maxHeight), 340.dp)
                    if (touchpad) {
                        Touchpad(
                            onDirection = vm::tap,
                            onOk = { vm.tap(KeyEvent.KEYCODE_DPAD_CENTER) },
                            modifier = Modifier.size(side),
                        )
                    } else {
                        DPad(
                            onDirection = vm::tap,
                            onOkDown = { vm.keyDown(KeyEvent.KEYCODE_DPAD_CENTER) },
                            onOkUp = { vm.keyUp(KeyEvent.KEYCODE_DPAD_CENTER) },
                            modifier = Modifier.size(side),
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HeldKey(vm, KeyEvent.KEYCODE_BACK, Icons.AutoMirrored.Rounded.ArrowBack, "رجوع")
                        HeldKey(vm, KeyEvent.KEYCODE_HOME, Icons.Rounded.Home, "الرئيسية")
                        RoundKey(
                            Icons.Rounded.Keyboard, "لوحة المفاتيح",
                            onDown = { vm.startTyping(); showKeyboard = true },
                            highlighted = textField,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Rocker(
                            label = volume?.let { if (it.muted) "صامت" else "${it.level}" } ?: "صوت",
                            upIcon = Icons.Rounded.Add,
                            downIcon = Icons.Rounded.Remove,
                            onUp = { vm.tap(KeyEvent.KEYCODE_VOLUME_UP) },
                            onDownKey = { vm.tap(KeyEvent.KEYCODE_VOLUME_DOWN) },
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MicButton(
                                listening = listening,
                                onPress = { if (hasMic()) vm.voicePress() else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                onRelease = { if (hasMic()) vm.voiceRelease() },
                            )
                            RoundKey(
                                Icons.Rounded.VolumeOff, "كتم الصوت",
                                onDown = { vm.tap(KeyEvent.KEYCODE_VOLUME_MUTE) },
                                size = 48.dp,
                                highlighted = volume?.muted == true,
                            )
                        }
                        Rocker(
                            label = "قناة",
                            upIcon = Icons.Rounded.KeyboardArrowUp,
                            downIcon = Icons.Rounded.KeyboardArrowDown,
                            onUp = { vm.tap(KeyEvent.KEYCODE_CHANNEL_UP) },
                            onDownKey = { vm.tap(KeyEvent.KEYCODE_CHANNEL_DOWN) },
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundKey(Icons.Rounded.FastRewind, "ترجيع", { vm.tap(KeyEvent.KEYCODE_MEDIA_REWIND) }, size = 54.dp, repeat = true)
                        RoundKey(Icons.Rounded.PlayArrow, "تشغيل/إيقاف", { vm.tap(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, size = 54.dp)
                        RoundKey(Icons.Rounded.FastForward, "تقديم", { vm.tap(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }, size = 54.dp, repeat = true)
                        RoundKey(Icons.Rounded.Settings, "الإعدادات", { vm.tap(KeyEvent.KEYCODE_SETTINGS) }, size = 54.dp)
                    }
                }
            }
        }

        VoiceBubble(listening, spoken, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }

    if (showKeyboard) {
        KeyboardSheet(vm, textField) { showKeyboard = false }
    }
    if (showApps) {
        AppsSheet(onLaunch = { vm.launchApp(it); showApps = false }, onDismiss = { showApps = false })
    }
}

@Composable
private fun HeldKey(vm: RemoteViewModel, code: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    RoundKey(icon, label, onDown = { vm.keyDown(code) }, onUp = { vm.keyUp(code) })
}

@Composable
private fun TopBar(
    name: String,
    state: ConnState,
    touchpad: Boolean,
    onDevices: () -> Unit,
    onApps: () -> Unit,
    onToggleMode: () -> Unit,
    onPowerDown: () -> Unit,
    onPowerUp: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onDevices)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(RC.surface),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Tv, null, tint = RC.accent, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, color = RC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (label, color) = when (state) {
                    ConnState.Connected -> "متصل" to RC.ok
                    ConnState.Connecting -> "جارٍ الاتصال…" to RC.warn
                    ConnState.Pairing -> "بانتظار الاقتران" to RC.warn
                    ConnState.Idle -> "غير متصل" to RC.muted
                    is ConnState.Failed -> "غير متصل" to RC.danger
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = RC.muted, fontSize = 12.sp)
                }
            }
        }
        IconButton(onClick = onApps) { Icon(Icons.Rounded.Apps, "التطبيقات", tint = RC.text) }
        IconButton(onClick = onToggleMode) {
            Icon(if (touchpad) Icons.Rounded.Gamepad else Icons.Rounded.TouchApp, "تبديل وضع التحكم", tint = RC.text)
        }
        Spacer(Modifier.width(4.dp))
        RoundKey(Icons.Rounded.PowerSettingsNew, "تشغيل/إطفاء", onDown = onPowerDown, onUp = onPowerUp, size = 46.dp, tint = RC.danger)
    }
}

@Composable
private fun VoiceBubble(listening: Boolean, text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = listening || text != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RC.surfaceHi)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text ?: "تحدّث الآن…",
                color = if (text == null) RC.accent else RC.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
