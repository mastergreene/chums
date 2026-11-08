package club.chums.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PendingMessage(
    val id: String = UUID.randomUUID().toString(),
    val agent: String,
    val hubUrl: String,
    val text: String? = null,
    val audioFile: String? = null,
    val error: String? = null
)

/** Private durable messages; retry is explicit and keeps the same hub request ID. */
class Outbox(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val index = File(dir, "messages.json")
    init { dir.mkdirs() }
    fun read(): List<PendingMessage> = if (index.exists())
        json.decodeFromString(ListSerializer(PendingMessage.serializer()), index.readText()) else emptyList()

    private fun write(items: List<PendingMessage>) {
        val temp = File(dir, "messages.tmp")
        temp.writeText(json.encodeToString(ListSerializer(PendingMessage.serializer()), items))
        check(temp.renameTo(index)) { "Couldn't save messages" }
    }

    @Synchronized fun addText(agent: String, hubUrl: String, text: String): PendingMessage =
        PendingMessage(agent = agent, hubUrl = hubUrl, text = text).also { write(read() + it) }

    @Synchronized fun addVoice(agent: String, hubUrl: String, file: File): PendingMessage {
        val item = PendingMessage(agent = agent, hubUrl = hubUrl)
        val saved = File(dir, "${item.id}.wav")
        file.copyTo(saved)
        val message = item.copy(audioFile = saved.name)
        write(read() + message)
        file.delete()
        return message
    }

    @Synchronized fun failed(id: String, error: String) = write(read().map { if (it.id == id) it.copy(error = error) else it })
    @Synchronized fun remove(id: String) {
        val items = read()
        write(items.filterNot { it.id == id })
        items.find { it.id == id }?.audioFile?.let { File(dir, it).delete() }
    }
    fun audio(item: PendingMessage): File = File(dir, requireNotNull(item.audioFile))
}
