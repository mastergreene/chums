package club.chums.android.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.Json
import java.io.File

class OutboxTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun voiceAndDestinationSurviveRestartUntilDiscarded() {
        val dir = temp.newFolder()
        val source = temp.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val first = Outbox(dir)
        val item = first.addVoice("ray", "https://mac.example", source)
        assertFalse(source.exists())
        val restarted = Outbox(dir)
        assertEquals(item, restarted.read().single())
        assertArrayEquals(byteArrayOf(1, 2, 3), restarted.audio(item).readBytes())
        restarted.failed(item.id, "Connection lost")
        assertEquals(item.id, restarted.read().single().id)
        assertEquals("https://mac.example", restarted.read().single().hubUrl)
        restarted.remove(item.id)
        assertTrue(restarted.read().isEmpty())
        assertFalse(restarted.audio(item).exists())
    }

    @Test fun textAndVoiceStayBoundToTheirOwnAgents() {
        val outbox = Outbox(temp.newFolder())
        val ray = outbox.addText("ray", "https://mac.example", "Fix a test")
        val peeps = outbox.addVoice("peeps", "https://mac.example", temp.newFile().apply { writeText("wav") })
        outbox.remove(ray.id)
        assertEquals(listOf(peeps), Outbox(File(outbox.audio(peeps).parent!!)).read())
    }

    @Test fun decodesUpstreamArtifactsAndUnknownFields() {
        val state = Json { ignoreUnknownKeys = true }.decodeFromString<HubState>("""
            {"rev":2,"focus":"ray","spoken":[{"id":"s1","session":"session-id","agent":"ray","text":"Result","artifact":{"id":"aabbccddeeff","title":"Chart","kind":"page","w":780,"view":{"type":"html","name":"chart.html","source":true}}}]}
        """.trimIndent())
        assertEquals("html", state.spoken.single().artifact?.view?.type)
        assertFalse(state.phoneRequestReceipts)
    }

    @Test fun rejectsAddressesThatCouldHideCredentialsOrLoseTheApiPath() {
        assertTrue(validHubUrl("https://mac.example/"))
        assertTrue(validHubUrl("http://192.168.1.2:7718"))
        listOf("", "file:///tmp", "https://user:secret@mac.example", "https://mac.example/path", "https://mac.example/?token=x").forEach { assertFalse(it, validHubUrl(it)) }
    }

    @Test fun attemptedDeliveryAndUploadedFilesSurviveAProcessRestart() {
        val dir = temp.newFolder()
        val outbox = Outbox(dir)
        val item = outbox.addText("noodle", "https://mac.example", "Look at this", listOf("/mac/uploads/photo.png"))
        outbox.attempted(item.id)
        val recovered = Outbox(dir).read().single()
        assertTrue(recovered.attempted)
        assertEquals(item.id, recovered.id)
        assertEquals(listOf("/mac/uploads/photo.png"), recovered.files)
        assertEquals("noodle", recovered.agent)
    }

    @Test fun upstreamSoulCardsPermitMissingOrNullOptionalFields() {
        val card = Json { ignoreUnknownKeys = true }.decodeFromString<SoulCard>("""
            {"id":"noodle","name":"Noodle","owns":null,"refers":null,"voice":null,"body":null,"canUndo":false}
        """.trimIndent())
        assertEquals("Noodle", card.name)
        assertNull(card.owns)
        assertNull(card.body)
    }
}
