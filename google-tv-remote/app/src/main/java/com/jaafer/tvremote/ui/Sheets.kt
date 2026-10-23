package com.jaafer.tvremote.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaafer.tvremote.PairingUi
import com.jaafer.tvremote.RemoteViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(vm: RemoteViewModel, tvFieldReady: Boolean, onDismiss: () -> Unit) {
    val text by vm.keyboardText.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RC.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).imePadding()) {
            Text("الكتابة على التلفاز", color = RC.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (tvFieldReady) "حقل النص على التلفاز جاهز — ما تكتبه يظهر فورًا"
                else "اختر حقل بحث على التلفاز أولًا (العربية تحتاج حقلًا نشطًا)",
                color = if (tvFieldReady) RC.ok else RC.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = vm::typed,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                placeholder = { Text("اكتب هنا…") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitTyping() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RC.accent,
                    unfocusedBorderColor = RC.outline,
                ),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { vm.typed(text.dropLast(1)) }) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("حذف")
                }
                FilledTonalButton(onClick = { vm.typed("") }) {
                    Icon(Icons.Rounded.Clear, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("مسح")
                }
                FilledTonalButton(onClick = { vm.submitTyping() }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("إدخال")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        focus.requestFocus()
    }
}

private data class TvApp(val name: String, val link: String, val color: Color)

private val apps = listOf(
    TvApp("YouTube", "https://www.youtube.com", Color(0xFFFF0033)),
    TvApp("Netflix", "https://www.netflix.com/title", Color(0xFFE50914)),
    TvApp("شاهد", "https://shahid.mbc.net", Color(0xFF00C4B3)),
    TvApp("Prime Video", "https://app.primevideo.com", Color(0xFF1A98FF)),
    TvApp("Disney+", "https://www.disneyplus.com", Color(0xFF113CCF)),
    TvApp("YouTube Music", "https://music.youtube.com", Color(0xFFFF4E45)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSheet(onLaunch: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RC.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("فتح تطبيق على التلفاز", color = RC.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(apps) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(RC.surfaceHi)
                            .clickable { onLaunch(app.link) }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(app.color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(app.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(app.name, color = RC.text, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PairingDialog(
    ui: PairingUi,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember(ui.device) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = {},
        containerColor = RC.surface,
        title = { Text("الاقتران مع ${ui.device.name}", fontSize = 20.sp) },
        text = {
            Column {
                Text(
                    when {
                        !ui.canEnterCode -> ui.error ?: ""
                        ui.busy && code.isEmpty() -> "جارٍ الاتصال بالتلفاز…"
                        else -> "أدخل الرمز المكوّن من 6 خانات الظاهر على شاشة التلفاز"
                    },
                    color = RC.muted,
                )
                if (ui.canEnterCode) {
                    Spacer(Modifier.height(18.dp))
                    CodeField(code, enabled = !ui.busy, modifier = Modifier.focusRequester(focus)) { value ->
                        code = value
                        if (value.length == 6) onSubmit(value)
                    }
                    ui.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = RC.danger, fontSize = 13.sp)
                    }
                }
                if (ui.busy) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = RC.accent, trackColor = RC.surfaceHi)
                }
            }
        },
        confirmButton = {
            if (ui.canEnterCode) {
                TextButton(onClick = { onSubmit(code) }, enabled = code.length == 6 && !ui.busy) { Text("اقتران") }
            } else {
                TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } },
    )
    LaunchedEffect(ui.busy, ui.canEnterCode) {
        if (!ui.busy && ui.canEnterCode) {
            delay(100)
            runCatching { focus.requestFocus() }
        }
    }
}

@Composable
private fun CodeField(value: String, enabled: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = value,
            onValueChange = { raw -> onChange(raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)) },
            enabled = enabled,
            singleLine = true,
            modifier = modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val active = i == value.length
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(RC.surfaceHi)
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    if (active) RC.accent else RC.outline,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                value.getOrNull(i)?.toString() ?: "",
                                color = RC.text,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            },
        )
    }
}
