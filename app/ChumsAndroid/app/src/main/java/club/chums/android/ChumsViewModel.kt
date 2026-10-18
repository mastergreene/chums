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

/// What outlives a rotation: the hub connection, the recorder (a clip in progress) and the player.
class ChumsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE)
    val hubClient = HubClient(app)
    val audioRecorder = AudioRecorder(app)
    val audioPlayer = AudioPlayer(app)

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
        audioPlayer.shutdown()
    }
}
