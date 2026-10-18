package club.chums.android.data

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class AudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {
    private val tag = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var onPlaybackFinished: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TTS", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onPlaybackFinished?.invoke()
                }
                override fun onError(utteranceId: String?) {
                    onPlaybackFinished?.invoke()
                }
            })
            ttsReady = true
            Log.d(tag, "TextToSpeech initialized successfully")
        } else {
            Log.e(tag, "TTS init failed with status: $status")
        }
    }

    fun speakText(text: String) {
        stop()
        if (ttsReady && tts != null) {
            val params = android.os.Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reply_${System.currentTimeMillis()}")
        } else {
            onPlaybackFinished?.invoke()
        }
    }

    suspend fun playUrl(url: String, token: String? = null) = withContext(Dispatchers.Main) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                if (token != null && token.isNotEmpty()) {
                    val headers = mapOf("Authorization" to "Bearer $token")
                    setDataSource(context, android.net.Uri.parse(url), headers)
                } else {
                    setDataSource(url)
                }
                setOnCompletionListener {
                    stop()
                    onPlaybackFinished?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: $what, $extra")
                    stop()
                    onPlaybackFinished?.invoke()
                    true
                }
                prepareAsync()
                setOnPreparedListener { start() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio URL", e)
            onPlaybackFinished?.invoke()
        }
    }

    suspend fun playBase64(base64Data: String) = withContext(Dispatchers.IO) {
        try {
            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "reply_temp.m4a")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                stop()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        stop()
                        onPlaybackFinished?.invoke()
                    }
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing base64 audio", e)
            withContext(Dispatchers.Main) {
                onPlaybackFinished?.invoke()
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true || (tts?.isSpeaking == true)
}
