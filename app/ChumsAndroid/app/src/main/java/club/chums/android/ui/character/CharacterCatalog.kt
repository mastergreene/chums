package club.chums.android.ui.character

import androidx.compose.runtime.Composable
import club.chums.android.data.ChumsState

/** Character contract v1. A skin never selects an agent or a runtime. */
data class CharacterSpec(val id: String, val displayName: String, val fallback: String = "ray")
val characterCatalog = listOf(
    CharacterSpec("garfield", "Garfield"), CharacterSpec("peeps", "Peeps"),
    CharacterSpec("squint", "Squint"), CharacterSpec("ray", "Ray")
)
fun characterFor(id: String): CharacterSpec = characterCatalog.find { it.id == id.lowercase() }
    ?: characterCatalog.first { it.id == "ray" }

@Composable
fun CharacterFace(id: String, state: ChumsState) {
    // Explicit fallback poses keep new protocol states usable on every bundled rig.
    val pose = when (state) {
        ChumsState.COMPACTING -> ChumsState.THINKING
        ChumsState.LIMITED, ChumsState.UNKNOWN -> ChumsState.BLOCKED
        ChumsState.RECONNECTING -> ChumsState.SLEEP
        else -> state
    }
    when (characterFor(id).id) {
        "garfield" -> GarfieldRig(pose)
        "peeps" -> PeepsRig(pose)
        "squint" -> SquintRig(pose)
        else -> RayRig(pose)
    }
}
