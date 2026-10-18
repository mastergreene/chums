package club.chums.android.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/// The hub's watch API (its publicPort, 7718): a long poll of /v1/state, and what the phone says and answers.
/// The URL and the watch token are entered in Settings; nothing is built in.
class HubClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE)
    private var baseUrl: String = prefs.getString("hub_url", "") ?: ""
    private var token: String = prefs.getString("hub_token", "") ?: ""

    private val tag = "HubClient"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)                       // the hub holds a poll for up to 20 s
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hubState = MutableStateFlow<HubState?>(null)
    val hubState: StateFlow<HubState?> = _hubState.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // buffered, so a slow listener (a line being spoken) never holds up the poll
    private val _spokenEvents = MutableSharedFlow<Spoken>(extraBufferCapacity = 64)
    val spokenEvents: SharedFlow<Spoken> = _spokenEvents.asSharedFlow()

    private var pollJob: Job? = null
    private var rev = 0
    private val seenSpoken = LinkedHashSet<String>()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    fun updateConfig(url: String, newToken: String) {
        baseUrl = url.trim().trimEnd('/')
        token = newToken.trim()
        prefs.edit().putString("hub_url", baseUrl).putString("hub_token", token).apply()
        restart()
    }

    fun getBaseUrl(): String = baseUrl
    fun getToken(): String = token

    fun start() {
        if (pollJob?.isActive == true || !isConfigured) return
        pollJob = scope.launch { pollLoop() }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _connected.value = false
    }

    fun restart() {
        stop()
        rev = 0
        seenSpoken.clear()
        _hubState.value = null
        start()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun request(path: String) = Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer $token")

    private suspend fun pollLoop() {
        var first = true
        var failures = 0
        while (coroutineContext.isActive) {
            try {
                val parsed = client.newCall(request("/v1/state?since=${if (first) 0 else rev}&device=phone").get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw HubError(if (resp.code == 401) "Wrong token" else "HTTP ${resp.code}")
                    json.decodeFromString<HubState>(resp.body?.string() ?: throw HubError("Empty answer"))
                }
                rev = parsed.rev
                _hubState.value = parsed
                _connected.value = true
                _lastError.value = null
                failures = 0
                // what was already said when the app connected is shown, not spoken again
                for (line in parsed.spoken) {
                    if (seenSpoken.add(line.id) && !first) _spokenEvents.tryEmit(line)
                }
                while (seenSpoken.size > 500) seenSpoken.remove(seenSpoken.first())
                first = false
            } catch (e: Exception) {
                if (!coroutineContext.isActive) break
                _connected.value = false
                _lastError.value = (e as? HubError)?.message ?: "Can't reach the hub"
                Log.w(tag, "poll failed", e)
                failures++
                delay(if (failures > 3) 5000 else 1000)
            }
        }
    }

    private class HubError(message: String) : Exception(message)

    suspend fun sendUtterance(file: File, agent: String, approvalId: String? = null): RouteResult? = withContext(Dispatchers.IO) {
        val builder = request("/v1/utterance")
            .post(file.asRequestBody("audio/wav".toMediaType()))
            .header("X-Chums-Agent", agent)
            .header("X-Chums-Device", "phone")
        if (approvalId != null) builder.header("X-Chums-Approval", approvalId)
        try {
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.let { json.decodeFromString<RouteResult>(it) }
                else { Log.e(tag, "sendUtterance failed: ${resp.code}"); null }
            }
        } catch (e: Exception) {
            Log.e(tag, "sendUtterance error", e)
            null
        } finally {
            file.delete()
        }
    }

    suspend fun verdict(requestId: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(VerdictRequest.serializer(), VerdictRequest(requestId, if (allow) "allow" else "deny"))
        try {
            client.newCall(request("/v1/verdict").post(payload.toRequestBody("application/json".toMediaType())).build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(tag, "verdict error", e)
            false
        }
    }

    suspend fun say(text: String, agent: String, approvalId: String? = null): RouteResult? = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(SayRequest.serializer(), SayRequest(text = text, agent = agent, approval = approvalId))
        try {
            client.newCall(request("/v1/say").post(payload.toRequestBody("application/json".toMediaType())).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.let { json.decodeFromString<RouteResult>(it) } else null
            }
        } catch (e: Exception) {
            Log.e(tag, "say error", e)
            null
        }
    }
}
