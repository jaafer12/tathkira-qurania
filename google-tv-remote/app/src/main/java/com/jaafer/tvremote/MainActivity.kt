package com.jaafer.tvremote

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaafer.tvremote.ui.DevicesScreen
import com.jaafer.tvremote.ui.PairingDialog
import com.jaafer.tvremote.ui.RC
import com.jaafer.tvremote.ui.RemoteScreen
import com.jaafer.tvremote.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { RemoteTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    // The phone's volume buttons control the TV while the remote is open.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && vm.handlesVolumeKeys()) {
            vm.tap(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && vm.handlesVolumeKeys()) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(code: Int) = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
}

@Composable
private fun App(vm: RemoteViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = screen == Screen.Devices && device != null) { vm.showRemote() }

    Scaffold(containerColor = RC.bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
                when (s) {
                    Screen.Devices -> DevicesScreen(vm)
                    Screen.Remote -> RemoteScreen(vm)
                }
            }
        }
    }

    pairing?.let { ui ->
        PairingDialog(ui, onSubmit = vm::submitCode, onRetry = vm::restartPairing, onCancel = vm::cancelPairing)
    }
}
