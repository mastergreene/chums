package club.chums.android.ui.screen

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.chums.android.data.AudioPlayer
import club.chums.android.data.AudioRecorder
import club.chums.android.data.ChumsState
import club.chums.android.data.HubClient
import club.chums.android.ui.character.RayRig
import club.chums.android.ui.components.ApprovalCard
import club.chums.android.ui.components.ToolBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    hubClient: HubClient,
    audioRecorder: AudioRecorder,
    audioPlayer: AudioPlayer,
    isPttKeyPressed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val hubState by hubClient.hubState.collectAsState()
    val isConnected by hubClient.connected.collectAsState()
    val lastError by hubClient.lastError.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var isTouchingPtt by remember { mutableStateOf(false) }
    var latestSubtitle by remember { mutableStateOf<String?>(null) }
    var localOverrideState by remember { mutableStateOf<ChumsState?>(null) }

    // Active agent: prefer online garfield, or any online agent, fallback to garfield
    val activeAgent = remember(hubState) {
        hubState?.agents?.find { it.id == "garfield" && it.status != "offline" }?.id
            ?: hubState?.agents?.find { it.status != "offline" }?.id
            ?: "garfield"
    }

    LaunchedEffect(Unit) {
        audioPlayer.onPlaybackFinished = {
            if (localOverrideState == ChumsState.SPEAKING) {
                localOverrideState = null
            }
        }
    }

    // Derive active state from hub
    val derivedState = remember(hubState, isConnected, localOverrideState, isTouchingPtt, isPttKeyPressed) {
        when {
            isTouchingPtt || isPttKeyPressed -> ChumsState.LISTENING
            localOverrideState != null -> localOverrideState!!
            !isConnected -> ChumsState.SLEEP
            (hubState?.approvals?.isNotEmpty() == true) -> ChumsState.APPROVAL
            else -> {
                val primarySession = hubState?.sessions?.firstOrNull()
                when (primarySession?.status?.lowercase()) {
                    "working" -> ChumsState.WORKING
                    "thinking" -> ChumsState.THINKING
                    "speaking" -> ChumsState.SPEAKING
                    "blocked" -> ChumsState.BLOCKED
                    "waiting" -> ChumsState.NEEDS_YOU
                    "done" -> ChumsState.DONE
                    else -> ChumsState.IDLE
                }
            }
        }
    }

    // Handle incoming spoken events (play audio and show subtitle)
    LaunchedEffect(Unit) {
        hubClient.spokenEvents.collect { line ->
            latestSubtitle = line.text
            localOverrideState = ChumsState.SPEAKING

            if (line.audioData != null) {
                audioPlayer.playBase64(line.audioData)
            } else if (line.audio != null) {
                val fullUrl = "${hubClient.getBaseUrl()}${line.audio}"
                audioPlayer.playUrl(fullUrl, hubClient.getToken())
            } else {
                audioPlayer.speakText(line.text)
            }

            // Fallback clear speaking state after reasonable duration if player doesn't callback
            delay(5000)
            if (localOverrideState == ChumsState.SPEAKING) {
                localOverrideState = null
            }
        }
    }

    // Handle PTT hardware key changes
    LaunchedEffect(isPttKeyPressed) {
        if (isPttKeyPressed) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            audioRecorder.startRecording()
        } else {
            val audioFile = audioRecorder.stopRecording()
            if (audioFile != null) {
                localOverrideState = ChumsState.THINKING
                scope.launch {
                    val res = hubClient.sendUtterance(audioFile, activeAgent)
                    if (res?.transcript != null && res.transcript.isNotEmpty()) {
                        latestSubtitle = "You: \"${res.transcript}\""
                    }
                    localOverrideState = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0C11))
    ) {
        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection status pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF15101F))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFE64E4B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) activeAgent.replaceFirstChar { it.uppercase() } else (lastError ?: "Offline"),
                    color = Color(0xFFECECEC),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0x99ECECEC)
                )
            }
        }

        // CENTER: CHARACTER RIG & TOOL BADGE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                RayRig(state = derivedState)

                // Floating tool badge overlay
                val activeTool = hubState?.tools?.lastOrNull()
                if (derivedState == ChumsState.WORKING) {
                    ToolBadge(
                        currentTool = activeTool,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitle text if agent is speaking
            AnimatedVisibility(
                visible = latestSubtitle != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = latestSubtitle.orEmpty(),
                    color = Color(0xFFECECEC),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .background(Color(0x6615101F), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }
        }

        // BOTTOM: APPROVAL CARD OR PTT BUTTON
        val activeApproval = hubState?.approvals?.firstOrNull()
        if (activeApproval != null) {
            ApprovalCard(
                approval = activeApproval,
                onVerdict = { allow ->
                    scope.launch {
                        hubClient.verdict(activeApproval.requestId, allow)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            // Push-To-Talk Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isTouchingPtt || isPttKeyPressed) Color(0xFFF2AE08) else Color(0xFF15101F))
                    .border(1.5.dp, Color(0xFFF2AE08), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isTouchingPtt = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                audioRecorder.startRecording()

                                tryAwaitRelease()

                                isTouchingPtt = false
                                val audioFile = audioRecorder.stopRecording()
                                if (audioFile != null) {
                                    localOverrideState = ChumsState.THINKING
                                    scope.launch {
                                        val res = hubClient.sendUtterance(audioFile, activeAgent)
                                        if (res?.transcript != null && res.transcript.isNotEmpty()) {
                                            latestSubtitle = "You: \"${res.transcript}\""
                                        }
                                        localOverrideState = null
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Push to Talk",
                    tint = if (isTouchingPtt || isPttKeyPressed) Color(0xFF040201) else Color(0xFFF2AE08),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // SETTINGS DIALOG
        if (showSettings) {
            SettingsDialog(
                initialUrl = hubClient.getBaseUrl(),
                initialToken = hubClient.getToken(),
                onSave = { newUrl, newToken ->
                    hubClient.updateConfig(newUrl, newToken)
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}
