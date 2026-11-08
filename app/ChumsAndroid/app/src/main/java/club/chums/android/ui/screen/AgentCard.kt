package club.chums.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import club.chums.android.data.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCard(agent: AgentInfo, runtimes: List<String>, hub: HubClient, onDismiss: () -> Unit) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scope = rememberCoroutineScope()
    var soul by remember(agent.id) { mutableStateOf<SoulCard?>(null) }
    var models by remember { mutableStateOf<Map<String, ModelChoices>>(emptyMap()) }
    var policies by remember { mutableStateOf<Map<String, PermissionChoices>>(emptyMap()) }
    var voices by remember { mutableStateOf<VoiceList?>(null) }
    var runtime by remember(agent.runtime) { mutableStateOf(agent.runtime ?: "claude") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editField by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    suspend fun loadSoul() { soul = json.decodeFromString<SoulCard>(hub.get("/v1/agents/${agent.id}/soul")) }
    LaunchedEffect(agent.id) {
        try { loadSoul() } catch (e: Exception) { message = e.message }
        try { models = json.decodeFromString<Map<String, ModelChoices>>(hub.get("/v1/models")) } catch (_: Exception) { }
        try { policies = json.decodeFromString<Map<String, PermissionChoices>>(hub.get("/v1/permissions")) } catch (_: Exception) { }
        try { voices = json.decodeFromString<VoiceList>(hub.get("/v1/voices")) } catch (_: Exception) { }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(soul?.name ?: agent.name, style = MaterialTheme.typography.headlineMedium)
            Text(agent.status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            Text("Runs on", style = MaterialTheme.typography.labelMedium)
            Row {
                listOf("claude" to "Claude Code", "codex" to "Codex").forEach { (id, label) ->
                    FilterChip(selected = runtime == id, enabled = !busy && id in runtimes, onClick = {
                        scope.launch {
                            busy = true
                            try { hub.setRuntime(agent.id, id); runtime = id; message = null }
                            catch (e: Exception) { message = e.message }
                            finally { busy = false }
                        }
                    }, label = { Text(label) })
                    Spacer(Modifier.width(8.dp))
                }
            }
            TextButton(enabled = !busy && models.containsKey(runtime), onClick = { picker = "model" }) {
                Text("Model · ${agent.model ?: models[runtime]?.default ?: "Agent default"}")
            }
            TextButton(enabled = !busy && policies.containsKey(runtime), onClick = { picker = "permission" }) {
                Text("Permissions · ${agent.permission ?: policies[runtime]?.default ?: "Agent default"}")
            }
            TextButton(enabled = !busy && voices?.voices?.isNotEmpty() == true, onClick = { picker = "voice" }) {
                Text("Voice · ${agent.voice ?: "Default"}")
            }
            if (voices?.ready == false) Text("Mac voices are unavailable. Android can read replies aloud.")
            Spacer(Modifier.height(24.dp))
            Text("Soul", style = MaterialTheme.typography.titleMedium)
            val fields = listOf("name" to ("Name" to soul?.name), "owns" to ("What it does" to soul?.owns),
                "refers" to ("Hands off" to soul?.refers), "voice" to ("How it talks" to soul?.voice), "body" to ("Notes" to soul?.body))
            fields.forEach { (field, value) ->
                TextButton(enabled = !busy && soul != null, onClick = { editField = field; editText = value.second.orEmpty() }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(value.first, style = MaterialTheme.typography.labelMedium)
                        Text(value.second?.ifBlank { "Tap to add" } ?: "Tap to add", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (soul?.canUndo == true) TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try { soul = json.decodeFromString<SoulCard>(hub.configureAgent(agent.id, "soul/undo", emptyMap())); message = "Undid the last change." }
                    catch (e: Exception) { message = e.message }
                    finally { busy = false }
                }
            }) { Text("Undo the last change") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            Text("Changes apply to your agent on the Mac.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
    editField?.let { field ->
        val limit = when (field) { "name" -> 24; "owns", "refers" -> 180; "voice" -> 90; else -> 2500 }
        AlertDialog(onDismissRequest = { editField = null }, title = { Text("Edit ${field.replaceFirstChar { it.uppercase() }}") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it.take(limit) }, supportingText = { Text("${editText.length}/$limit") }) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try { soul = json.decodeFromString<SoulCard>(hub.configureAgent(agent.id, "soul/set", mapOf("field" to field, "value" to editText))); editField = null; message = "Saved." }
                    catch (e: Exception) { message = e.message }
                    finally { busy = false }
                }
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editField = null }) { Text("Cancel") } })
    }
    picker?.let { kind ->
        val choices = when (kind) {
            "model" -> listOf(Choice("", "Agent default")) + models[runtime]?.models.orEmpty()
            "permission" -> listOf(Choice("", "Agent default")) + policies[runtime]?.modes.orEmpty()
            else -> voices?.voices.orEmpty().map { Choice(it.id, it.name) }
        }
        AlertDialog(onDismissRequest = { picker = null }, title = { Text("Choose $kind") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { choice -> TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val key = if (kind == "permission") "mode" else kind
                            hub.configureAgent(agent.id, kind, mapOf(key to choice.id.ifBlank { null }))
                            picker = null; message = "Saved."
                        } catch (e: Exception) { message = e.message }
                        finally { busy = false }
                    }
                }) { Column(Modifier.fillMaxWidth()) { Text(choice.name); choice.about?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
            }
        }, confirmButton = { TextButton(onClick = { picker = null }) { Text("Close") } })
    }
}
