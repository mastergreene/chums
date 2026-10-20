package club.chums.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StateContractTest {
    private fun state(status: String, subagents: Int = 0) = stateFor(
        HubState(1, sessions = listOf(SessionInfo("session", "garfield", status, subagents = subagents))), "garfield")
    @Test fun mapsNonIdleStatuses() {
        val cases = mapOf("thinking" to ChumsState.THINKING, "compacting" to ChumsState.COMPACTING,
            "limited" to ChumsState.LIMITED, "working" to ChumsState.WORKING,
            "blocked" to ChumsState.BLOCKED, "waiting" to ChumsState.NEEDS_YOU,
            "done" to ChumsState.DONE, "future_status" to ChumsState.UNKNOWN)
        cases.forEach { (wire, expected) -> assertEquals(wire, expected, state(wire)) }
        assertEquals(ChumsState.SUBAGENTS, state("working", 2))
    }
    @Test fun approvalsBelongToTheirAgent() {
        val hub = HubState(1, sessions = listOf(SessionInfo("s", "patch", "working")),
            approvals = listOf(Approval("a", "garfield", "Bash", "Run", "pwd")))
        assertEquals(ChumsState.APPROVAL, stateFor(hub, "garfield"))
        assertEquals(ChumsState.WORKING, stateFor(hub, "patch"))
    }
}
