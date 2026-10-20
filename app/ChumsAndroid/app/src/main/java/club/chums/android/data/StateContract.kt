package club.chums.android.data

/** Wire status mapping v1. Unknown statuses must never imply that work has finished. */
fun stateFor(hub: HubState?, agent: String): ChumsState {
    if (hub == null) return ChumsState.SLEEP
    if (hub.approvals.any { it.session == agent }) return ChumsState.APPROVAL
    val session = hub.sessions.find { it.name == agent }
    val status = session?.status ?: hub.agents?.find { it.id == agent }?.status
    return when (status) {
        "idle" -> ChumsState.IDLE
        "listening" -> ChumsState.LISTENING
        "thinking" -> ChumsState.THINKING
        "working" -> if ((session?.subagents ?: 0) > 0) ChumsState.SUBAGENTS else ChumsState.WORKING
        "subagents" -> ChumsState.SUBAGENTS
        "compacting" -> ChumsState.COMPACTING
        "speaking" -> ChumsState.SPEAKING
        "done" -> ChumsState.DONE
        "waiting", "needs_you" -> ChumsState.NEEDS_YOU
        "reconnecting" -> ChumsState.RECONNECTING
        "limited" -> ChumsState.LIMITED
        "blocked" -> ChumsState.BLOCKED
        "error" -> ChumsState.ERROR
        "offline", "sleep", null -> ChumsState.SLEEP
        else -> ChumsState.UNKNOWN
    }
}

fun runtimeLabel(runtime: String?): String = when (runtime) {
    "hermes" -> "Standalone chat (not Hermes Agent)"
    "hermes-agent" -> "Hermes Agent (native ACP)"
    else -> runtime ?: "Unknown backend"
}
