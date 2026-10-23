package com.jaafer.tvremote.tv

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Streams the phone microphone to the TV so its own Google Assistant handles the request,
 * exactly like the mic button of the original remote.
 */
class VoiceStreamer(private val client: RemoteClient) {

    /** Returns false when the TV did not accept a voice session. */
    @SuppressLint("MissingPermission")
    suspend fun stream(isHeld: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK) * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext false
        }
        try {
            coroutineScope {
                // Record while the TV opens the session so the first words aren't lost.
                val session = async { client.beginVoice(2_000) }
                recorder.startRecording()
                val pending = ByteArrayOutputStream()
                val buffer = ByteArray(1_600) // 100 ms
                val started = SystemClock.elapsedRealtime()
                var id: Int? = null
                while (isActive) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n > 0) pending.write(buffer, 0, n)
                    if (id == null && session.isCompleted) id = session.await() ?: return@coroutineScope false
                    val held = isHeld() && SystemClock.elapsedRealtime() - started < MAX_MS
                    if (id != null && (pending.size() >= CHUNK || !held)) {
                        if (pending.size() > 0) client.voicePayload(id, pending.toByteArray())
                        pending.reset()
                    }
                    if (!held) break
                }
                val sessionId = id ?: session.await() ?: return@coroutineScope false
                if (pending.size() > 0) client.voicePayload(sessionId, pending.toByteArray())
                client.endVoice(sessionId)
                true
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 8_000 // 16-bit mono PCM, as the TV expects
        const val CHUNK = 8_192
        const val MAX_MS = 15_000L
    }
}
