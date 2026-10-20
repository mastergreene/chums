package club.chums.android.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import club.chums.android.data.Spoken
import kotlinx.coroutines.launch

@Composable
fun ConversationDialog(agent: String, lines: List<Spoken>, onDismiss: () -> Unit, onSend: suspend (String) -> Boolean) {
    var draft by remember(agent) { mutableStateOf("") }
    var sending by remember(agent) { mutableStateOf(false) }
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
                }
            }
            OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Message") }, enabled = !sending)
            error?.let { Text(it) }
        } },
        confirmButton = { TextButton(enabled = draft.isNotBlank() && !sending, onClick = {
            scope.launch {
                sending = true
                try {
                    if (onSend(draft)) { draft = ""; error = null }
                    else error = "Message was not confirmed. Check the conversation before retrying."
                } finally { sending = false }
            }
        }) { Text(if (sending) "Sending…" else "Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
