package com.jaafer.tvremote

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaafer.tvremote.tv.CertManager
import com.jaafer.tvremote.tv.PairingSession
import com.jaafer.tvremote.tv.RemoteClient
import com.jaafer.tvremote.tv.TvDevice
import com.jaafer.tvremote.tv.TvDiscovery
import com.jaafer.tvremote.tv.VoiceStreamer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Devices, Remote }

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data object Pairing : ConnState
    data class Failed(val message: String) : ConnState
}

data class PairingUi(
    val device: TvDevice,
    val busy: Boolean = true,
    val error: String? = null,
    val canEnterCode: Boolean = true,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val certs = CertManager(app)
    private val client = RemoteClient(certs, viewModelScope)
    private val voice = VoiceStreamer(client)
    val discovery = TvDiscovery(app)

    val screen = MutableStateFlow(if (prefs.lastDevice != null) Screen.Remote else Screen.Devices)
    val state = MutableStateFlow<ConnState>(ConnState.Idle)
    val device = MutableStateFlow(prefs.lastDevice)
    val savedDevices = MutableStateFlow(prefs.savedDevices)
    val pairing = MutableStateFlow<PairingUi?>(null)
    val touchpad = MutableStateFlow(prefs.touchpad)
    val listening = MutableStateFlow(false)
    val spokenText = MutableStateFlow<String?>(null)
    val keyboardText = MutableStateFlow("")
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val volume: StateFlow<RemoteClient.Volume?> = client.volume
    val textFieldActive: StateFlow<Boolean> = client.textFieldActive

    private var connectJob: Job? = null
    private var pairingSession: PairingSession? = null
    private var wantConnection = prefs.lastDevice != null
    private var inForeground = false
    private var voiceHeld = false
    private var voicePressedAt = 0L
    private var streamUnsupported = false
    private var recognizer: SpeechRecognizer? = null
    private val keyMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    init {
        client.onDisconnected = { viewModelScope.launch { onConnectionLost() } }
        viewModelScope.launch(Dispatchers.IO) { certs.sslContext() } // warm up key generation
    }

    // ---- Navigation & connection ----

    fun showDevices() { screen.value = Screen.Devices }

    fun showRemote() { if (device.value != null) screen.value = Screen.Remote }

    fun connect(target: TvDevice, attempts: Int = 2) {
        device.value = target
        screen.value = Screen.Remote
        wantConnection = true
        connectJob?.cancel()
        pairingSession?.close()
        pairingSession = null
        pairing.value = null
        connectJob = viewModelScope.launch {
            state.value = ConnState.Connecting
            var left = attempts
            while (true) {
                try {
                    client.connect(target.host)
                    state.value = ConnState.Connected
                    prefs.lastDevice = target
                    prefs.save(target)
                    savedDevices.value = prefs.savedDevices
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RemoteClient.NotPairedException) {
                    beginPairing(target)
                    return@launch
                } catch (e: Exception) {
                    if (--left <= 0) {
                        state.value = ConnState.Failed("تعذّر الاتصال بالتلفاز")
                        return@launch
                    }
                    delay(700)
                }
            }
        }
    }

    fun retry() { device.value?.let { connect(it) } }

    fun addManual(ip: String) {
        val host = ip.trim()
        if (host.isNotEmpty()) connect(TvDevice(host, host))
    }

    fun forget(target: TvDevice) {
        prefs.forget(target)
        savedDevices.value = prefs.savedDevices
        if (device.value?.host == target.host) {
            wantConnection = false
            connectJob?.cancel()
            client.close()
            device.value = null
            state.value = ConnState.Idle
        }
    }

    private suspend fun onConnectionLost() {
        if (!wantConnection || !inForeground) {
            state.value = ConnState.Idle
            return
        }
        state.value = ConnState.Connecting
        delay(300)
        device.value?.let { connect(it, attempts = 3) }
    }

    fun onForeground() {
        inForeground = true
        val target = device.value ?: return
        val s = state.value
        if (wantConnection && (s == ConnState.Idle || s is ConnState.Failed)) connect(target)
    }

    fun onBackground() {
        inForeground = false
        stopVoice()
        if (state.value == ConnState.Connected || state.value == ConnState.Connecting) {
            connectJob?.cancel()
            client.close()
            state.value = ConnState.Idle
        }
    }

    // ---- Pairing ----

    private suspend fun beginPairing(target: TvDevice) {
        state.value = ConnState.Pairing
        pairing.value = PairingUi(target, busy = true)
        val session = PairingSession(certs)
        pairingSession = session
        try {
            withContext(Dispatchers.IO) { session.start(target.host, "TV Remote (${Build.MODEL})") }
            pairing.value = PairingUi(target, busy = false)
        } catch (e: CancellationException) {
            session.close()
            throw e
        } catch (e: Exception) {
            session.close()
            pairingSession = null
            pairing.value = PairingUi(
                target, busy = false, canEnterCode = false,
                error = "تعذّر بدء الاقتران. تأكد أن التلفاز يعمل وعلى نفس الشبكة.",
            )
        }
    }

    fun submitCode(code: String) {
        val session = pairingSession ?: return
        val current = pairing.value ?: return
        pairing.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { session.finish(code) }
                session.close()
                pairingSession = null
                pairing.value = null
                connect(current.device)
            } catch (e: PairingSession.WrongCodeException) {
                pairing.value = current.copy(busy = false, error = "الرمز غير صحيح، تحقق منه وحاول مجددًا")
            } catch (e: Exception) {
                session.close()
                pairingSession = null
                pairing.value = current.copy(busy = false, canEnterCode = false, error = "فشل الاقتران، أعد المحاولة")
            }
        }
    }

    fun restartPairing() {
        val target = pairing.value?.device ?: return
        pairingSession?.close()
        connectJob?.cancel()
        connectJob = viewModelScope.launch { beginPairing(target) }
    }

    fun cancelPairing() {
        connectJob?.cancel()
        pairingSession?.close()
        pairingSession = null
        pairing.value = null
        state.value = ConnState.Failed("لم يكتمل الاقتران")
    }

    // ---- Keys ----

    private fun ready(): Boolean {
        if (client.isConnected) return true
        val s = state.value
        if (s != ConnState.Connecting && s != ConnState.Pairing) {
            messages.tryEmit("غير متصل بالتلفاز")
            retry()
        }
        return false
    }

    /** A single key press (used for keys that repeat while held). */
    fun tap(code: Int) {
        if (ready()) client.key(code)
    }

    /** Key down/up mirror the physical remote, so long presses behave natively on the TV. */
    fun keyDown(code: Int) {
        if (!ready()) return
        client.key(code, RemoteClient.START_LONG)
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) client.textFieldActive.value = false
    }

    fun keyUp(code: Int) {
        if (client.isConnected) client.key(code, RemoteClient.END_LONG)
    }

    fun launchApp(link: String) {
        if (ready()) client.launch(link)
    }

    fun toggleTouchpad() {
        touchpad.value = !touchpad.value
        prefs.touchpad = touchpad.value
    }

    fun handlesVolumeKeys() = screen.value == Screen.Remote && client.isConnected

    // ---- Keyboard ----

    fun startTyping() { keyboardText.value = "" }

    fun typed(text: String) {
        val old = keyboardText.value
        keyboardText.value = text
        if (!client.isConnected) return
        if (client.textFieldActive.value) {
            client.sendText(text)
            return
        }
        // No text field reported by the TV: fall back to individual key events.
        val common = old.commonPrefixWith(text).length
        repeat(old.length - common) { client.key(KeyEvent.KEYCODE_DEL) }
        val added = text.substring(common).lowercase()
        if (added.isEmpty()) return
        keyMap.getEvents(added.toCharArray())
            ?.filter { it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(it.keyCode) }
            ?.forEach { client.key(it.keyCode) }
    }

    fun submitTyping() = tap(KeyEvent.KEYCODE_ENTER)

    // ---- Voice ----

    fun voicePress() {
        if (!ready()) return
        if (listening.value) {
            stopVoice()
            return
        }
        voiceHeld = true
        voicePressedAt = SystemClock.elapsedRealtime()
        listening.value = true
        spokenText.value = null
        if (streamUnsupported) {
            startRecognizer()
            return
        }
        viewModelScope.launch {
            val streamed = runCatching { voice.stream { voiceHeld } }.getOrDefault(false)
            if (streamed) {
                listening.value = false
                return@launch
            }
            // The TV didn't open a voice session: recognize on the phone and type the result.
            streamUnsupported = true
            if (voiceHeld) startRecognizer() else listening.value = false
        }
    }

    fun voiceRelease() {
        // A quick tap keeps listening (tap again to stop); holding is push-to-talk.
        if (SystemClock.elapsedRealtime() - voicePressedAt < 350) return
        stopVoice()
    }

    private fun stopVoice() {
        voiceHeld = false
        recognizer?.stopListening()
    }

    private fun startRecognizer() {
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listening.value = false
            messages.tryEmit("التعرف على الصوت غير متاح على هذا الهاتف")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(results: Bundle) {
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { spokenText.value = it }
                }

                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    finishRecognizer()
                    if (!text.isNullOrBlank()) deliverSpoken(text)
                }

                override fun onError(error: Int) = finishRecognizer()
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
        }
    }

    private fun finishRecognizer() {
        recognizer?.destroy()
        recognizer = null
        listening.value = false
    }

    private fun deliverSpoken(text: String) {
        spokenText.value = text
        viewModelScope.launch {
            if (!client.textFieldActive.value) {
                client.key(KeyEvent.KEYCODE_SEARCH)
                delay(1_500)
            }
            client.sendText(text)
            delay(2_000)
            spokenText.value = null
        }
    }

    override fun onCleared() {
        recognizer?.destroy()
        pairingSession?.close()
        discovery.stop()
        client.close()
    }
}
