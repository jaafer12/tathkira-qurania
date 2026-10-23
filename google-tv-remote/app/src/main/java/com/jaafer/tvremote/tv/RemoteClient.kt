package com.jaafer.tvremote.tv

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Control connection on port 6466 (Android TV Remote protocol v2). */
class RemoteClient(private val certs: CertManager, private val scope: CoroutineScope) {
    class NotPairedException : IOException("Device is not paired")
    data class Volume(val level: Int, val max: Int, val muted: Boolean)

    val volume = MutableStateFlow<Volume?>(null)
    val powered = MutableStateFlow(true)
    val textFieldActive = MutableStateFlow(false)
    val currentApp = MutableStateFlow("")
    var onDisconnected: (() -> Unit)? = null

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var outbox: Channel<ByteArray>? = null
    @Volatile private var connected = false
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0
    @Volatile private var lastPing = 0L
    @Volatile private var voiceSession: CompletableDeferred<Int>? = null
    private var jobs = emptyList<Job>()

    val isConnected get() = connected

    suspend fun connect(host: String) = withContext(Dispatchers.IO) {
        close()
        val s = certs.sslContext().socketFactory.createSocket() as SSLSocket
        try {
            s.connect(InetSocketAddress(host, PORT), 4000)
            s.tcpNoDelay = true
            s.startHandshake()
        } catch (e: SSLException) {
            runCatching { s.close() }
            throw NotPairedException()
        } catch (e: IOException) {
            runCatching { s.close() }
            throw e
        }
        socket = s
        lastPing = 0L
        val ready = CompletableDeferred<Unit>()
        val out = Channel<ByteArray>(Channel.UNLIMITED)
        outbox = out

        val reader = scope.launch(Dispatchers.IO) {
            try {
                val input = s.inputStream.buffered()
                while (isActive) handle(ProtoMessage.parse(input.readDelimited()), ready)
            } catch (_: Exception) {
            } finally {
                // A TV that doesn't know our certificate drops the connection right away.
                ready.completeExceptionally(NotPairedException())
                dropped(s)
            }
        }
        val writer = scope.launch(Dispatchers.IO) {
            val os = s.outputStream
            try {
                for (msg in out) os.writeDelimited(msg)
            } catch (_: IOException) {
                runCatching { s.close() }
            }
        }
        val watchdog = scope.launch {
            // The TV pings every few seconds; silence means the link is dead.
            while (isActive) {
                delay(5_000)
                val last = lastPing
                if (last != 0L && SystemClock.elapsedRealtime() - last > 30_000) {
                    runCatching { s.close() }
                    break
                }
            }
        }
        jobs = listOf(reader, writer, watchdog)
        try {
            withTimeout(6_000) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            close()
            throw IOException("Timed out")
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun close() {
        val s = socket
        socket = null
        connected = false
        outbox?.close()
        outbox = null
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        runCatching { s?.close() }
    }

    fun key(code: Int, direction: Int = SHORT) = send {
        message(10) { int(1, code); int(2, direction) }
    }

    fun launch(appLink: String) = send { message(90) { string(1, appLink) } }

    /** Replaces the content of the text field focused on the TV. */
    fun sendText(text: String) = send {
        message(21) {
            int(1, imeCounter)
            int(2, fieldCounter)
            message(3) {
                int(1, 1)
                message(2) {
                    int(1, text.length - 1)
                    int(2, text.length - 1)
                    string(3, text)
                }
            }
        }
    }

    /** Asks the TV to open a voice session; returns its id or null if unsupported. */
    suspend fun beginVoice(timeoutMs: Long): Int? {
        val session = CompletableDeferred<Int>()
        voiceSession = session
        send { message(30) }
        return withTimeoutOrNull(timeoutMs) { session.await() }.also { voiceSession = null }
    }

    fun voicePayload(session: Int, samples: ByteArray) = send {
        message(31) { int(1, session); bytes(2, samples) }
    }

    fun endVoice(session: Int) = send { message(32) { int(1, session) } }

    private fun send(block: ProtoWriter.() -> Unit) {
        outbox?.trySend(proto(block))
    }

    private fun handle(m: ProtoMessage, ready: CompletableDeferred<Unit>) {
        when {
            m.has(1) -> { // remote_configure
                send {
                    message(1) {
                        int(1, FEATURES)
                        message(2) {
                            string(1, Build.MODEL)
                            string(2, Build.MANUFACTURER)
                            int(3, 1)
                            string(4, "1")
                            string(5, "atvremote")
                            string(6, "1.0.0")
                        }
                    }
                }
                connected = true
                ready.complete(Unit)
            }
            m.has(2) -> send { message(2) { int(1, FEATURES) } } // remote_set_active
            m.has(8) -> { // remote_ping_request
                lastPing = SystemClock.elapsedRealtime()
                val value = m.message(8)?.int(1) ?: 0
                send { message(9) { int(1, value) } }
            }
            m.has(20) -> m.message(20)?.message(1)?.let { currentApp.value = it.string(12) }
            m.has(21) -> m.message(21)?.let { // remote_ime_batch_edit
                imeCounter = it.int(1)
                fieldCounter = it.int(2)
                textFieldActive.value = true
            }
            m.has(22) -> { // remote_ime_show_request
                m.message(22)?.message(2)?.let { fieldCounter = it.int(1) }
                textFieldActive.value = true
            }
            m.has(30) -> voiceSession?.complete(m.message(30)?.int(1) ?: 0)
            m.has(40) -> powered.value = m.message(40)?.bool(1) ?: true
            m.has(50) -> m.message(50)?.let { volume.value = Volume(it.int(7), it.int(6), it.bool(8)) }
        }
    }

    private fun dropped(s: SSLSocket) {
        if (socket !== s) return
        socket = null
        val wasConnected = connected
        connected = false
        if (wasConnected) onDisconnected?.invoke()
    }

    companion object {
        const val PORT = 6466
        const val START_LONG = 1
        const val END_LONG = 2
        const val SHORT = 3
        // ping | key | ime | voice | power | volume | app link
        private const val FEATURES = 1 or 2 or 4 or 8 or 32 or 64 or 512
    }
}
