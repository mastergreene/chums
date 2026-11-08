package club.chums.android.data

import kotlinx.serialization.Serializable

enum class ChumsState {
    IDLE,
    LISTENING,
    THINKING,
    WORKING,
    SUBAGENTS,
    SPEAKING,
    DONE,
    NEEDS_YOU,
    APPROVAL,
    BLOCKED,
    DIZZY,
    ERROR,
    SLEEP,
    COMPACTING,
    LIMITED,
    RECONNECTING,
    UNKNOWN
}

@Serializable
data class SessionInfo(
    val id: String,
    val name: String,
    val status: String,
    val lastLine: String? = null,
    val subagents: Int? = null
)

@Serializable
data class ToolInfo(
    val id: String,
    val session: String,
    val type: String,
    val text: String,
    val ok: Boolean? = null,
    val at: Double? = null
)

@Serializable
data class Approval(
    val requestId: String,
    val session: String,
    val tool: String,
    val description: String,
    val preview: String = "",
    val summary: String? = null
)

@Serializable
data class Spoken(
    val id: String,
    val session: String,
    val text: String,
    val kind: String = "reply",
    val audio: String? = null,
    val audioData: String? = null,
    val device: String? = null,
    val at: Double? = null,
    val agent: String? = null,
    val artifact: Artifact? = null
)

@Serializable
data class Artifact(val id: String, val title: String = "Result", val kind: String = "image", val view: ArtifactView? = null)

@Serializable
data class ArtifactView(val type: String = "image", val url: String? = null, val name: String? = null)

@Serializable
data class AgentInfo(
    val id: String,
    val name: String,
    val character: String,
    val status: String,
    val line: String? = null,
    val needs: Boolean = false,
    val owns: String? = null,
    val runtime: String? = null,
    val voice: String? = null,
    val model: String? = null,
    val permission: String? = null
)

@Serializable
data class HubState(
    val rev: Int,
    val phoneRequestReceipts: Boolean = false,
    val sessions: List<SessionInfo> = emptyList(),
    val approvals: List<Approval> = emptyList(),
    val spoken: List<Spoken> = emptyList(),
    val tools: List<ToolInfo>? = null,
    val agents: List<AgentInfo>? = null,
    val runtimes: List<String>? = null
)

@Serializable
data class VerdictRequest(
    val requestId: String,
    val behavior: String
)

@Serializable
data class SayRequest(
    val text: String,
    val agent: String,
    val device: String = "phone",
    val approval: String? = null,
    val files: List<String> = emptyList()
)

@Serializable
data class UploadedFile(val path: String, val name: String = "File", val artifact: Artifact? = null)

@Serializable
data class SoulCard(val id: String? = null, val name: String = "", val owns: String? = null, val refers: String? = null, val voice: String? = null, val body: String? = null, val canUndo: Boolean = false)

@Serializable
data class Choice(val id: String, val name: String, val about: String? = null)

@Serializable
data class ModelChoices(val default: String? = null, val models: List<Choice> = emptyList())

@Serializable
data class PermissionChoices(val default: String? = null, val modes: List<Choice> = emptyList())

@Serializable
data class VoiceInfo(val id: String, val name: String, val language: String = "")

@Serializable
data class VoiceList(val ready: Boolean = false, val voices: List<VoiceInfo> = emptyList())

@Serializable
data class RouteResult(
    val transcript: String? = null,
    val kind: String = "utterance",
    val echo: String? = null,
    val target: String? = null,
    val id: String? = null,
    val confirmed: Boolean? = null
)
