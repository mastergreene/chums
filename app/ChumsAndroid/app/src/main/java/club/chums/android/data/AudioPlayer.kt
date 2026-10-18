package club.chums.android.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioPlayer(private val context: Context) {
    private val tag = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    var onPlaybackFinished: (() -> Unit)? = null

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
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
}
