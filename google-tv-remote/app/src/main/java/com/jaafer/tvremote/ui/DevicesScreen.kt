package com.jaafer.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaafer.tvremote.RemoteViewModel
import com.jaafer.tvremote.tv.TvDevice

@Composable
fun DevicesScreen(vm: RemoteViewModel) {
    val found by vm.discovery.devices.collectAsStateWithLifecycle()
    val scanning by vm.discovery.scanning.collectAsStateWithLifecycle()
    val saved by vm.savedDevices.collectAsStateWithLifecycle()
    val current by vm.device.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        vm.discovery.start()
        onDispose { vm.discovery.stop() }
    }

    val savedHosts = saved.map { it.host }.toSet()
    val all = found + saved.filter { s -> found.none { it.host == s.host } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                IconButton(onClick = vm::showRemote) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = RC.text) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("ريموت التلفاز", color = RC.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, null, tint = RC.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("اختر جهاز Google TV المتصل بنفس شبكة Wi‑Fi", color = RC.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الأجهزة", color = RC.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (scanning) {
                CircularProgressIndicator(Modifier.size(16.dp), color = RC.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ البحث…", color = RC.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(all, key = { it.host }) { d ->
                DeviceRow(
                    device = d,
                    online = found.any { it.host == d.host },
                    saved = d.host in savedHosts,
                    selected = current?.host == d.host,
                    onClick = { vm.connect(d) },
                    onForget = { vm.forget(d) },
                )
            }
            if (all.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Tv, null, tint = RC.outline, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("لم يتم العثور على أجهزة بعد", color = RC.muted)
                        Text("تأكد أن التلفاز يعمل وعلى نفس الشبكة", color = RC.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { addDialog = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("إضافة يدويًا عبر عنوان IP")
        }
    }

    if (addDialog) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = RC.surface,
            title = { Text("عنوان IP للتلفاز") },
            text = {
                Column {
                    Text("تجده في التلفاز: الإعدادات ← الشبكة والإنترنت", color = RC.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("192.168.1.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { addDialog = false; vm.addManual(ip) }, enabled = ip.count { it == '.' } == 3) {
                    Text("اتصال")
                }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun DeviceRow(
    device: TvDevice,
    online: Boolean,
    saved: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) RC.accent.copy(alpha = 0.12f) else RC.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(RC.surfaceHi), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Tv, null, tint = if (online) RC.accent else RC.muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, color = RC.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                buildString {
                    append(device.host)
                    if (saved) append(" · مقترن")
                    if (!online) append(" · غير ظاهر حاليًا")
                },
                color = RC.muted,
                fontSize = 12.sp,
            )
        }
        if (saved) {
            IconButton(onClick = onForget) { Icon(Icons.Rounded.Close, "إزالة", tint = RC.muted) }
        }
    }
}
