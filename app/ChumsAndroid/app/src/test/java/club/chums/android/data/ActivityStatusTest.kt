package club.chums.android.data

import org.junit.Assert.*
import org.junit.Test

class ActivityStatusTest {
    private val agent = AgentInfo("ray", "Ray", "ray", "idle")
    @Test fun completedToolsNeverAppearAsCurrentWork() {
        val hub = HubState(1, tools = listOf(ToolInfo("a", "ray", "bash", "npm test"), ToolInfo("b", "ray", "stop", "Stopped")))
        assertEquals(ActivityStatus("Idle"), activityStatus(hub, agent, true))
        assertEquals(ActivityStatus("Thinking", "About your message"), activityStatus(hub.copy(sessions = listOf(SessionInfo("s", "ray", "working"))), agent, true))
    }
    @Test fun onlyWorkAfterTheLastStopAndForThisAgentIsShown() {
        val hub = HubState(1, sessions = listOf(SessionInfo("s", "ray", "working")), tools = listOf(
            ToolInfo("a", "ray", "read", "Old file"), ToolInfo("b", "ray", "stop", "Stopped"),
            ToolInfo("c", "noodle", "bash", "Other command"), ToolInfo("d", "ray", "read", "New file")))
        assertEquals("New file", activityStatus(hub, agent, true).detail)
    }
    @Test fun disconnectedStateOverridesOldWorkAndApprovals() {
        val hub = HubState(1, sessions = listOf(SessionInfo("s", "ray", "working")), approvals = listOf(Approval("a", "ray", "Bash", "Run")))
        assertEquals("Can't reach your Mac", activityStatus(hub, agent, false).title)
        assertEquals("Needs you", activityStatus(hub, agent, true).title)
    }
}
