package club.chums.android

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import club.chums.android.data.AudioPlayer
import club.chums.android.data.AudioRecorder
import club.chums.android.data.HubClient
import club.chums.android.data.Outbox
import club.chums.android.data.PendingMessage
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File

/// What outlives a rotation: the hub connection, the recorder (a clip in progress) and the player.
class ChumsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE)
    val hubClient = HubClient(app)
    val audioRecorder = AudioRecorder(app)
    val audioPlayer = AudioPlayer(app)
    private val outbox = Outbox(File(app.filesDir, "outbox"))
    var pending by mutableStateOf(outbox.read())
        private set
    var sending by mutableStateOf<Set<String>>(emptySet())
        private set

    suspend fun sendText(text: String, agent: String): Boolean {
        val message = withContext(Dispatchers.IO) { outbox.addText(agent, hubClient.getBaseUrl(), text) }
        pending = outbox.read()
        retry(message)
        return true // retained locally even when the hub is temporarily unavailable
    }

    fun sendVoice(file: File, agent: String) {
        // ViewModel owns delivery so closing a dialog or rotating cannot cancel an upload.
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) { outbox.addVoice(agent, hubClient.getBaseUrl(), file) }
            pending = outbox.read()
            retry(message)
        }
    }

    suspend fun retry(message: PendingMessage) {
        if (message.id in sending) return
        sending = sending + message.id
        try {
            check(message.hubUrl == hubClient.getBaseUrl()) { "This message belongs to another hub. Restore its address in Settings to send it." }
            check(hubClient.hubState.value?.phoneRequestReceipts == true && hubClient.connected.value) {
                "Connect to the updated Mac hub before sending. Message saved on this phone."
            }
            val result = if (message.text != null) hubClient.say(message.text, message.agent, message.id)
                else hubClient.sendUtterance(outbox.audio(message), message.agent, message.id)
            // A successful HTTP response can still mean no agent was running.
            if (result.kind !in setOf("order", "verdict", "status") || (result.kind == "verdict" && result.confirmed != true)) {
                outbox.failed(message.id, "${result.echo ?: "Not delivered."} Check the conversation, then discard and send a new message when the agent is ready.")
            } else outbox.remove(message.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outbox.failed(message.id, e.message ?: "Couldn't reach the hub. Message saved.")
        } finally {
            pending = outbox.read()
            sending = sending - message.id
        }
    }

    fun discard(message: PendingMessage) {
        if (message.id in sending) return
        outbox.remove(message.id)
        pending = outbox.read()
    }

    /// Whether the volume-up key talks (hold to speak) while the app is in front.
    var volumeKeyTalks by mutableStateOf(prefs.getBoolean("volume_key_talks", true))
        private set

    fun updateVolumeKeyTalks(on: Boolean) {
        volumeKeyTalks = on
        prefs.edit().putBoolean("volume_key_talks", on).apply()
    }

    init { hubClient.start() }

    override fun onCleared() {
        hubClient.close()
        audioRecorder.cancelRecording()
        audioPlayer.shutdown()
    }
}
