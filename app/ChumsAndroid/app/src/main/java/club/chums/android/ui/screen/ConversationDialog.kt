package club.chums.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import club.chums.android.data.Spoken
import club.chums.android.data.Artifact
import club.chums.android.data.PendingMessage
import kotlinx.coroutines.launch

@Composable
fun ConversationDialog(agent: String, lines: List<Spoken>, onDismiss: () -> Unit, onSend: suspend (String) -> Boolean,
    onArtifact: (Artifact) -> Unit, pending: List<PendingMessage>, sending: Set<String>,
    onRetry: (PendingMessage) -> Unit, onDiscard: (PendingMessage) -> Unit) {
    var draft by remember(agent) { mutableStateOf("") }
    var composing by remember(agent) { mutableStateOf(false) }
    var error by remember(agent) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation · $agent") },
        text = { Column {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                Text("Recent hub history. Older messages may be unavailable.")
                lines.forEach { line ->
                    Text(if (line.kind == "echo") "You: ${line.text}" else "${line.kind}: ${line.text}", Modifier.padding(vertical = 6.dp))
                    line.artifact?.let { artifact -> TextButton(onClick = { onArtifact(artifact) }) { Text("Open ${artifact.title}") } }
                }
                pending.forEach { item ->
                    Text("Saved ${if (item.text == null) "voice message" else "message"}: ${item.text ?: "Recording"}", Modifier.padding(top = 12.dp))
                    Text(if (item.id in sending) "Sending…" else item.error ?: "Waiting to send")
                    Text("Retry checks the same request ID. If delivery is uncertain, check the agent conversation first.")
                    Row {
                        TextButton(enabled = item.id !in sending, onClick = { onRetry(item) }) { Text("Check / retry") }
                        TextButton(enabled = item.id !in sending, onClick = { onDiscard(item) }) { Text("Discard") }
                    }
                }
            }
            OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Message") }, enabled = !composing)
            error?.let { Text(it) }
        } },
        confirmButton = { TextButton(enabled = draft.isNotBlank() && !composing, onClick = {
            scope.launch {
                composing = true
                try {
                    if (onSend(draft)) { draft = ""; error = null }
                    else error = "Message was not confirmed. Check the conversation before retrying."
                } finally { composing = false }
            }
        }) { Text(if (composing) "Sending…" else "Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
