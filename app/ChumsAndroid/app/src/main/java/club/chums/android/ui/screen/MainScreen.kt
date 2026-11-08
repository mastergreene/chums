package club.chums.android.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.chums.android.ChumsViewModel
import club.chums.android.data.*
import club.chums.android.ui.character.OriginalCharacter
import club.chums.android.ui.character.originalCharacters
import club.chums.android.ui.components.ApprovalCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val originalColors = darkColorScheme(primary = Color.White, onPrimary = Color.Black,
    surface = Color(0xFF111111), background = Color.Black, tertiary = Color(0xFFFFBD66))

@Composable
fun MainScreen(model: ChumsViewModel, isPttKeyPressed: Boolean = false) {
    MaterialTheme(colorScheme = originalColors) { OriginalHome(model, isPttKeyPressed) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginalHome(model: ChumsViewModel, isPttKeyPressed: Boolean) {
    val hub = model.hubClient
    val state by hub.hubState.collectAsState()
    val connected by hub.connected.collectAsState()
    val connectionError by hub.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scroll = rememberLazyListState()
    var settings by remember { mutableStateOf(!hub.isConfigured) }
    var agentCard by remember { mutableStateOf(false) }
    var front by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordingAgent by remember { mutableStateOf("noodle") }
    var speaking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Artifact?>(null) }
    val known = originalCharacters.map { AgentInfo(it, it.replaceFirstChar(Char::uppercase), it, "offline", runtime = "claude") }
    val agents = state?.agents ?: known
    val active = agents.find { it.id == model.selectedAgent } ?: agents.firstOrNull() ?: known.first()
    val currentAgent by rememberUpdatedState(active.id)
    val lines = state?.spoken.orEmpty().filter { it.agent == active.id || (it.agent == null && it.session == active.id) }
    val pending = model.pending.filter { it.agent == active.id }
    val approval = state?.approvals?.firstOrNull { it.session == active.id }
    val pose = when { recording -> ChumsState.LISTENING; speaking -> ChumsState.SPEAKING; !connected -> ChumsState.RECONNECTING; else -> stateFor(state, active.id) }
    val activity = activityStatus(state, active, connected)
    val recentTools = state?.tools.orEmpty().filter { it.session == active.id }
    val currentTools = recentTools.drop(recentTools.indexOfLast { it.type == "stop" } + 1)
    val lastReply = lines.lastOrNull { it.kind !in setOf("echo", "verdict", "refused") }
    fun startRecording(): Boolean {
        model.audioPlayer.stop(); recordingAgent = currentAgent
        recording = model.audioRecorder.startRecording()
        if (!recording) notice = "Allow microphone access in Android Settings, or type your message."
        return recording
    }
    fun stopRecording(cancel: Boolean = false) {
        recording = false
        val destination = recordingAgent
        scope.launch { model.audioRecorder.stopRecording()?.let { if (cancel) it.delete() else model.sendVoice(it, destination) } }
    }
    LaunchedEffect(isPttKeyPressed) { if (isPttKeyPressed) startRecording() else if (recording) stopRecording() }
    DisposableEffect(Unit) { onDispose { model.audioRecorder.cancelRecording() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recording) {
                model.audioRecorder.cancelRecording(); recording = false
                notice = "Recording cancelled when Chums left the screen."
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        hub.spokenEvents.collect { line ->
            if (line.kind in setOf("echo", "verdict", "refused") || line.agent != currentAgent || (line.device != null && line.device != "phone") || !model.readReplies) return@collect
            speaking = true
            try { model.audioPlayer.play(line, hub.getBaseUrl(), hub.getToken(), 3000L + line.text.length * 90L) }
            finally { speaking = false }
        }
    }
    LaunchedEffect(active.id, lines.size, pending.size) {
        if (scroll.layoutInfo.totalItemsCount > 0) scroll.animateScrollToItem(scroll.layoutInfo.totalItemsCount - 1)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val destination = active.id
        scope.launch {
            uploading = true
            try {
                for (uri in uris) {
                    val uploaded = uploadAttachment(context, uri, hub)
                    check(currentAgent == destination) { "Agent changed while uploading. Add the file again to the intended conversation." }
                    model.attachments = model.attachments + uploaded
                }
                notice = null
            } catch (e: Exception) { notice = e.message }
            finally { uploading = false }
        }
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = Color(0xFF111111)) {
            Text("Agents", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(agents, key = { it.id }) { agent ->
                    NavigationDrawerItem(selected = agent.id == active.id, onClick = {
                        if (recording) stopRecording(cancel = true)
                        model.audioPlayer.stop(); model.selectAgent(agent.id); model.attachments = emptyList()
                        scope.launch { drawer.close() }
                    }, label = { Column { Text(agent.name); Text(if (agent.needs) "Needs you" else agent.status, fontSize = 12.sp) } },
                        icon = { OriginalAvatar(agent.character, Modifier.size(36.dp)) },
                        badge = { if (agent.needs) Text("●", color = MaterialTheme.colorScheme.tertiary) }, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            TextButton(onClick = { settings = true; scope.launch { drawer.close() } }, modifier = Modifier.padding(16.dp)) { Text("Settings") }
        }
    }) {
        Scaffold(containerColor = Color.Black, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Agents") }
                TextButton(onClick = { agentCard = true }, modifier = Modifier.weight(1f)) { Text(active.name, style = MaterialTheme.typography.titleMedium) }
                IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Settings") }
            }
        }, bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().imePadding().padding(12.dp)) {
                approval?.let { card ->
                    var error by remember(card.requestId) { mutableStateOf<String?>(null) }
                    ApprovalCard(card, onVerdict = { allow -> scope.launch { error = if (hub.verdict(card.requestId, allow)) null else "Decision not confirmed. Check the Mac." } }, error = error)
                }
                if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.attachments.forEach { file -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.name, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { model.attachments = model.attachments - file }) { Icon(Icons.Default.Close, "Remove attachment") }
                } }
                Row(Modifier.fillMaxWidth().background(Color(0xFF1C1C1C), RoundedCornerShape(26.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !uploading && !sending, onClick = { if (recording) stopRecording(cancel = true) else filePicker.launch("*/*") }) {
                        Icon(if (recording) Icons.Default.Close else Icons.Default.Add, if (recording) "Discard recording" else "Add photos or files")
                    }
                    if (recording) Text("Listening…", Modifier.weight(1f))
                    else TextField(value = draft, onValueChange = { draft = it }, placeholder = { Text("Message ${active.name}") }, modifier = Modifier.weight(1f), maxLines = 5,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    if (draft.isNotBlank() || model.attachments.isNotEmpty()) IconButton(enabled = !sending && !uploading, onClick = {
                        val text = draft; val files = model.attachments.map { it.path }; val agent = active.id
                        scope.launch {
                            sending = true
                            try { model.sendText(text, agent, files); draft = ""; model.attachments = emptyList(); notice = null }
                            catch (e: Exception) { notice = e.message }
                            finally { sending = false }
                        }
                    }) { Icon(Icons.Default.ArrowUpward, "Send") }
                    else Box(Modifier.size(48.dp).pointerInput(active.id) {
                        detectTapGestures(onPress = {
                            val wasRecording = recording; val start = System.currentTimeMillis()
                            if (!wasRecording && !startRecording()) return@detectTapGestures
                            var handled = false
                            try {
                                val released = tryAwaitRelease()
                                if (!released) stopRecording(cancel = true)
                                else if (wasRecording || System.currentTimeMillis() - start >= 250) stopRecording()
                                handled = true
                            } finally {
                                if (!handled) { model.audioRecorder.cancelRecording(); recording = false }
                            }
                        })
                    }, contentAlignment = Alignment.Center) { Icon(if (recording) Icons.Default.ArrowUpward else Icons.Default.Mic, if (recording) "Send recording" else "Tap or hold to talk") }
                }
            }
        }) { padding ->
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!connected) item { Text(connectionError ?: "Can't reach your Mac", color = MaterialTheme.colorScheme.tertiary) }
                items(lines, key = { it.id }) { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.kind == "echo") Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 340.dp).background(if (line.kind == "echo") Color(0xFF333333) else Color(0xFF1C1C1C), RoundedCornerShape(18.dp)).padding(14.dp)) {
                            Text(line.text)
                            line.artifact?.let { artifact -> TextButton(onClick = { viewing = artifact }) { Text("Open ${artifact.title}") } }
                        }
                    }
                }
                pending.forEach { saved -> item(key = saved.id) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Saved on this phone: ${saved.text ?: "Voice recording"}")
                        Text(saved.error ?: "Waiting to send", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                        Row {
                            TextButton(enabled = saved.id !in model.sending, onClick = { scope.launch { model.retry(saved) } }) { Text("Check / send") }
                            TextButton(enabled = saved.id !in model.sending, onClick = { model.discard(saved) }) { Text("Discard") }
                        }
                    }
                } }
                item {
                    val large = front || lines.isEmpty()
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (large) Arrangement.Center else Arrangement.Start) {
                        OriginalCharacter(active.character, pose, Modifier.size(if (large) 260.dp else 100.dp),
                            caption = lastReply?.text.orEmpty(), tools = currentTools.map { it.text }.takeLast(6),
                            subagents = state?.sessions?.find { it.name == active.id }?.subagents ?: 0,
                            eventId = lastReply?.id.orEmpty(), kind = lastReply?.kind.orEmpty(),
                            toolId = recentTools.lastOrNull()?.id.orEmpty(), toolFailed = recentTools.lastOrNull()?.ok == false)
                        if (!large) Column(Modifier.padding(start = 12.dp)) {
                            Text(if (recording) "Listening" else if (speaking) "Speaking" else activity.title)
                            activity.detail?.let { Text(it, fontSize = 12.sp, maxLines = 3) }
                            TextButton(onClick = { front = true }) { Text("Expand") }
                        }
                    }
                    if (large) {
                        Text(if (recording) "Listening" else if (speaking) "Speaking" else activity.title)
                        activity.detail?.let { Text(it, fontSize = 12.sp) }
                        if (front) TextButton(onClick = { front = false }) { Text("Back to conversation") }
                    }
                }
                notice?.let { item { Text(it, color = MaterialTheme.colorScheme.tertiary) } }
            }
        }
    }
    viewing?.let { ArtifactDialog(it, hub, onDismiss = { viewing = null }) }
    if (agentCard) AgentCard(active, state?.runtimes ?: listOf("claude", "codex"), hub) { agentCard = false }
    if (settings) SettingsDialog(hub.getBaseUrl(), hub.getToken(), model.volumeKeyTalks,
        onSave = { url, token, key -> hub.updateConfig(url, token); model.updateVolumeKeyTalks(key); settings = false }, onDismiss = { settings = false },
        initialReadReplies = model.readReplies, onReadReplies = { model.readReplies(it) })
}

@Composable
private fun OriginalAvatar(id: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(id) { context.assets.open("avatars/avatar-${id.takeIf { it in originalCharacters } ?: "noodle"}.png").use { android.graphics.BitmapFactory.decodeStream(it) } }
    Image(bitmap!!.asImageBitmap(), id, modifier)
}

private suspend fun uploadAttachment(context: Context, uri: Uri, hub: HubClient): UploadedFile = withContext(Dispatchers.IO) {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "attachment" } ?: "attachment"
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 25 * 1024 * 1024) { "Files must be smaller than 25 MB" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: error("Couldn't read file")
    hub.upload(name, bytes)
}
