package club.chums.android.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import club.chums.android.ChumsViewModel
import club.chums.android.data.ChumsState
import club.chums.android.data.HubState
import club.chums.android.ui.character.GarfieldRig
import club.chums.android.ui.character.RayRig
import club.chums.android.ui.components.ApprovalCard
import club.chums.android.ui.components.ToolBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/// Lines the phone doesn't say aloud: your own words, the hub's "Allowed." and auto mode's refusals (as on the watch).
private val SILENT_KINDS = setOf("echo", "verdict", "refused")

/// The face for the agent on screen, from its own session and its own approvals (never another agent's).
private fun stateFor(hub: HubState?, agent: String): ChumsState {
    if (hub == null) return ChumsState.SLEEP
    val session = hub.sessions.find { it.name == agent }
    if (hub.approvals.any { it.session == agent }) return ChumsState.APPROVAL
    val status = session?.status ?: hub.agents?.find { it.id == agent }?.status
    return when (status) {
        "working", "compacting" -> if ((session?.subagents ?: 0) > 0) ChumsState.SUBAGENTS else ChumsState.WORKING
        "waiting" -> ChumsState.NEEDS_YOU
        "blocked" -> ChumsState.BLOCKED
        "offline", null -> ChumsState.SLEEP
        else -> ChumsState.IDLE
    }
}

@Composable
fun MainScreen(model: ChumsViewModel, isPttKeyPressed: Boolean = false) {
    val hubClient = model.hubClient
    val audioRecorder = model.audioRecorder
    val audioPlayer = model.audioPlayer
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val hubState by hubClient.hubState.collectAsState()
    val isConnected by hubClient.connected.collectAsState()
    val lastError by hubClient.lastError.collectAsState()

    var showSettings by remember { mutableStateOf(!hubClient.isConfigured) }
    var isTouchingPtt by remember { mutableStateOf(false) }
    var latestSubtitle by rememberSaveable { mutableStateOf<String?>(null) }
    var localOverrideState by remember { mutableStateOf<ChumsState?>(null) }
    var selectedAgent by rememberSaveable { mutableStateOf<String?>(null) }

    // the agent on screen: the one picked, else Garfield when it's up, else the first one up, else Garfield
    val activeAgent = selectedAgent
        ?: hubState?.agents?.find { it.id == "garfield" && it.status != "offline" }?.id
        ?: hubState?.agents?.find { it.status != "offline" }?.id
        ?: "garfield"
    val currentAgent by rememberUpdatedState(activeAgent)

    val battery = rememberBatteryPercent()
    val network = rememberNetworkKind()

    val derivedState = when {
        isTouchingPtt || isPttKeyPressed -> ChumsState.LISTENING
        localOverrideState != null -> localOverrideState!!
        !isConnected -> ChumsState.SLEEP
        else -> stateFor(hubState, activeAgent)
    }

    // the agent's lines, spoken one after another; the poll never waits for them (HubClient buffers)
    LaunchedEffect(Unit) {
        hubClient.spokenEvents.collect { line ->
            if (line.kind in SILENT_KINDS) return@collect
            if (line.agent != null && line.agent != currentAgent) return@collect
            if (line.device != null && line.device != "phone") return@collect
            latestSubtitle = line.text
            localOverrideState = ChumsState.SPEAKING
            // at most about 2.5 words a second plus some slack, so a player that never finishes can't hold the face
            audioPlayer.play(line, hubClient.getBaseUrl(), hubClient.getToken(), maxMs = 3000L + line.text.length * 90L)
            if (localOverrideState == ChumsState.SPEAKING) localOverrideState = null
        }
    }

    suspend fun send(file: File) {
        localOverrideState = ChumsState.THINKING
        val res = hubClient.sendUtterance(file, currentAgent)
        if (res == null) {
            localOverrideState = ChumsState.ERROR
            latestSubtitle = "The hub didn't get that."
            delay(2500)
        } else if (!res.transcript.isNullOrEmpty()) {
            latestSubtitle = "You: \"${res.transcript}\""
        }
        if (localOverrideState == ChumsState.THINKING || localOverrideState == ChumsState.ERROR) localOverrideState = null
    }

    LaunchedEffect(isPttKeyPressed) {
        if (isPttKeyPressed) {
            audioPlayer.stop()
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            audioRecorder.startRecording()
        } else {
            audioRecorder.stopRecording()?.let { scope.launch { send(it) } }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val rigSize = min(280.dp, min(maxWidth * 0.8f, maxHeight * 0.45f))

        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // hub dot and the agent pill: a tap goes to the next agent
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF15101F))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(99.dp))
                    .clickable {
                        val ids = hubState?.agents?.map { it.id }?.filter { it.isNotEmpty() }.orEmpty()
                        if (ids.isNotEmpty()) {
                            selectedAgent = ids[(ids.indexOf(activeAgent) + 1) % ids.size]
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFE64E4B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                val name = hubState?.agents?.find { it.id == activeAgent }?.name ?: activeAgent.replaceFirstChar { it.uppercase() }
                Text(
                    text = if (isConnected) "$name ▾" else (lastError ?: "Offline"),
                    color = Color(0xFFECECEC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFF15101F))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIcon(network.icon, network.label)
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusIcon(Icons.Default.BatteryStd, "Battery")
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = battery?.let { "$it%" } ?: "–",
                        color = Color(0xFFB0B0B0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0x99ECECEC))
                }
            }
        }

        // CENTER: the character, its tool badge, and the last line
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(rigSize), contentAlignment = Alignment.Center) {
                if (activeAgent.contains("garfield", ignoreCase = true)) GarfieldRig(state = derivedState)
                else RayRig(state = derivedState)
                if (derivedState == ChumsState.WORKING || derivedState == ChumsState.SUBAGENTS) {
                    ToolBadge(
                        currentTool = hubState?.tools?.lastOrNull { it.session == activeAgent },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = latestSubtitle != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = latestSubtitle.orEmpty(),
                    color = Color(0xFFECECEC),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .heightIn(max = 120.dp)
                        .background(Color(0xFF15101F), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }

        // BOTTOM: the agent's approval card, else the talk button
        val activeApproval = hubState?.approvals?.firstOrNull { it.session == activeAgent }
        if (activeApproval != null) {
            ApprovalCard(
                approval = activeApproval,
                onVerdict = { allow -> scope.launch { hubClient.verdict(activeApproval.requestId, allow) } },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isTouchingPtt || isPttKeyPressed) Color(0xFFF5902F) else Color(0xFF15101F))
                    .border(1.5.dp, Color(0xFFF5902F), CircleShape)
                    .pointerInput(Unit) {
                        // currentAgent and send() are read when the press ends, so a switch of agent is followed
                        detectTapGestures(
                            onPress = {
                                isTouchingPtt = true
                                audioPlayer.stop()
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                audioRecorder.startRecording()
                                tryAwaitRelease()
                                isTouchingPtt = false
                                audioRecorder.stopRecording()?.let { scope.launch { send(it) } }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hold to talk",
                    tint = if (isTouchingPtt || isPttKeyPressed) Color(0xFF120B07) else Color(0xFFF5902F),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                initialUrl = hubClient.getBaseUrl(),
                initialToken = hubClient.getToken(),
                initialVolumeKeyTalks = model.volumeKeyTalks,
                onSave = { newUrl, newToken, volumeKey ->
                    hubClient.updateConfig(newUrl, newToken)
                    model.updateVolumeKeyTalks(volumeKey)
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = label, tint = Color(0xFFB0B0B0), modifier = Modifier.size(14.dp))
}

/// The battery level, kept current by the system's sticky ACTION_BATTERY_CHANGED broadcast (null until known).
@Composable
private fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) percent = level * 100 / scale
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent.takeIf { it >= 0 }
}

private enum class NetworkKind(val icon: ImageVector, val label: String) {
    WIFI(Icons.Default.Wifi, "Wi-Fi"), CELLULAR(Icons.Default.SignalCellularAlt, "Mobile data"),
    VPN(Icons.Default.VpnKey, "VPN"), NONE(Icons.Default.WifiOff, "No network")
}

/// How the phone is online now (Wi-Fi, mobile data or not at all), from the system's default network.
@Composable
private fun rememberNetworkKind(): NetworkKind {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(NetworkKind.NONE) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                kind = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkKind.VPN      // Tailscale, over either
                    else -> NetworkKind.NONE
                }
            }
            override fun onLost(network: Network) { kind = NetworkKind.NONE }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }
    return kind
}
