package club.chums.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import club.chums.android.data.AudioPlayer
import club.chums.android.data.AudioRecorder
import club.chums.android.data.HubClient
import club.chums.android.ui.screen.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var hubClient: HubClient
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer

    private var isPttKeyPressed by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hubClient = HubClient(this)
        audioRecorder = AudioRecorder(this)
        audioPlayer = AudioPlayer(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        hubClient.start(lifecycleScope)

        setContent {
            MainScreen(
                hubClient = hubClient,
                audioRecorder = audioRecorder,
                audioPlayer = audioPlayer,
                isPttKeyPressed = isPttKeyPressed
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event?.repeatCount == 0) {
                isPttKeyPressed = true
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isPttKeyPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        hubClient.stop()
        audioPlayer.stop()
    }
}
