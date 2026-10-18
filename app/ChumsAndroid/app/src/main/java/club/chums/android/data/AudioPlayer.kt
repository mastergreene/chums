package club.chums.android.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/// Plays an agent's line: the hub's rendered voice when it sent one, else the phone's own text-to-speech.
class AudioPlayer(context: Context) : TextToSpeech.OnInitListener {
    private val tag = "AudioPlayer"
    private val context = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = TextToSpeech(this.context, this)
    private var ttsReady = false

    private val _playing = MutableStateFlow(false)
    /// True from the start of a line until it has been heard (or failed).
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { Log.e(tag, "TTS init failed: $status"); return }
        tts?.language = Locale.ENGLISH
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { _playing.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _playing.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _playing.value = false }
        })
        ttsReady = true
    }

    /// Plays one line and returns once it has been heard, or after `maxMs`.
    suspend fun play(line: Spoken, baseUrl: String, token: String, maxMs: Long) {
        when {
            line.audioData != null -> playBase64(line.audioData)
            line.audio != null -> playUrl("$baseUrl${line.audio}", token)
            else -> speakText(line.text)
        }
        withTimeoutOrNull(maxMs) { playing.first { !it } }
        if (_playing.value) stop()
    }

    private fun speakText(text: String) {
        stop()
        if (!ttsReady) return
        _playing.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "reply_${System.nanoTime()}")
    }

    private suspend fun playUrl(url: String, token: String) = withContext(Dispatchers.Main) {
        stop()
        startPlayer { setDataSource(context, Uri.parse(url), mapOf("Authorization" to "Bearer $token")) }
    }

    private suspend fun playBase64(data: String) {
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("reply_", ".m4a", context.cacheDir).apply { writeBytes(Base64.decode(data, Base64.DEFAULT)) }
        }
        withContext(Dispatchers.Main) {
            stop()
            startPlayer(onEnd = { file.delete() }) { setDataSource(file.absolutePath) }
        }
    }

    private fun startPlayer(onEnd: () -> Unit = {}, source: MediaPlayer.() -> Unit) {
        _playing.value = true
        try {
            mediaPlayer = MediaPlayer().apply {
                source()
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stop(); onEnd() }
                setOnErrorListener { _, what, extra -> Log.e(tag, "MediaPlayer error $what/$extra"); stop(); onEnd(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Can't play the line", e)
            stop(); onEnd()
        }
    }

    fun stop() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        try { tts?.stop() } catch (_: Exception) {}
        _playing.value = false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
