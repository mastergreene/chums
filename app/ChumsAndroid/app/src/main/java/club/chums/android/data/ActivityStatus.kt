package club.chums.android.data

data class ActivityStatus(val title: String, val detail: String? = null)

fun activityStatus(hub: HubState?, agent: AgentInfo, connected: Boolean): ActivityStatus {
    if (!connected) return ActivityStatus("Can't reach your Mac", "Trying again")
    if (hub?.approvals?.any { it.session == agent.id } == true) return ActivityStatus("Needs you", "Review the action below")
    val state = hub?.sessions?.find { it.name == agent.id }?.status ?: agent.status
    val tools = hub?.tools.orEmpty().filter { it.session == agent.id }
    val current = tools.drop(tools.indexOfLast { it.type == "stop" } + 1).lastOrNull()
    return when (state) {
        "working" -> ActivityStatus(if (current == null) "Thinking" else "Working", current?.text ?: "About your message")
        "compacting" -> ActivityStatus("Tidying its memory", "Making its conversation shorter")
        "limited" -> ActivityStatus("Rate limited", "Waiting for the agent")
        "waiting" -> ActivityStatus("Needs you", agent.line)
        "blocked" -> ActivityStatus("Stuck", agent.line)
        "offline" -> ActivityStatus("Not running on your Mac")
        "idle" -> ActivityStatus("Idle")
        else -> ActivityStatus(state.replaceFirstChar { it.uppercase() }, agent.line)
    }
}
