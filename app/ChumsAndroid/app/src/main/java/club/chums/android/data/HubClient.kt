package club.chums.android.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import android.content.Context
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class HubClient(
    private val context: Context? = null,
    private var baseUrl: String = "http://192.168.5.3:7718",
    private var token: String = "J0KQJUwc7vnX3EWBcOP3FX70"
) {
    init {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE)
            baseUrl = prefs.getString("hub_url", baseUrl) ?: baseUrl
            token = prefs.getString("hub_token", token) ?: token
        }
    }
    private val tag = "HubClient"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val _hubState = MutableStateFlow<HubState?>(null)
    val hubState: StateFlow<HubState?> = _hubState.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _spokenEvents = MutableSharedFlow<Spoken>()
    val spokenEvents: SharedFlow<Spoken> = _spokenEvents.asSharedFlow()

    private val _approvalEvents = MutableSharedFlow<Approval>()
    val approvalEvents: SharedFlow<Approval> = _approvalEvents.asSharedFlow()

    private var pollJob: Job? = null
    private var rev = 0
    private val seenSpoken = mutableSetOf<String>()
    private val seenApprovals = mutableSetOf<String>()

    fun updateConfig(url: String, newToken: String) {
        baseUrl = url.trimEnd('/')
        token = newToken
        context?.let { ctx ->
            ctx.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("hub_url", baseUrl)
                .putString("hub_token", token)
                .apply()
        }
        restart()
    }

    fun getBaseUrl(): String = baseUrl
    fun getToken(): String = token

    fun start(scope: CoroutineScope) {
        if (pollJob != null) return
        pollJob = scope.launch(Dispatchers.IO) {
            pollLoop()
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _connected.value = false
    }

    fun restart() {
        val scope = CoroutineScope(Dispatchers.IO)
        stop()
        rev = 0
        start(scope)
    }

    private suspend fun pollLoop() {
        var first = true
        var failureCount = 0

        while (CoroutineScope(Dispatchers.IO).isActive) {
            val since = if (first) 0 else rev
            val url = "$baseUrl/v1/state?since=$since&device=phone"

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            if (token.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            try {
                val call = client.newCall(requestBuilder.build())
                val response = withContext(Dispatchers.IO) { call.execute() }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val parsed = json.decodeFromString<HubState>(body)
                        rev = parsed.rev
                        _hubState.value = parsed
                        _connected.value = true
                        _lastError.value = null
                        failureCount = 0
                        first = false

                        // Check for new spoken lines
                        for (item in parsed.spoken) {
                            if (!seenSpoken.contains(item.id)) {
                                seenSpoken.add(item.id)
                                if (!first) _spokenEvents.emit(item)
                            }
                        }

                        // Check for new approvals
                        for (a in parsed.approvals) {
                            if (!seenApprovals.contains(a.requestId)) {
                                seenApprovals.add(a.requestId)
                                if (!first) _approvalEvents.emit(a)
                            }
                        }
                    }
                } else {
                    _connected.value = false
                    _lastError.value = "HTTP ${response.code}: ${response.message}"
                    failureCount++
                    delay(if (failureCount > 3) 5000 else 1000)
                }
            } catch (e: Exception) {
                _connected.value = false
                _lastError.value = e.localizedMessage ?: "Connection failed"
                failureCount++
                delay(if (failureCount > 3) 5000 else 1000)
            }
        }
    }

    suspend fun sendUtterance(file: File, agent: String, approvalId: String? = null): RouteResult? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/utterance"
        val mediaType = "audio/wav".toMediaType()
        val body = file.asRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("X-Chums-Agent", agent)
            .header("X-Chums-Device", "phone")

        if (approvalId != null) {
            requestBuilder.header("X-Chums-Approval", approvalId)
        }
        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val resp = client.newCall(requestBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()?.let { json.decodeFromString<RouteResult>(it) }
            } else {
                Log.e(tag, "sendUtterance failed: ${resp.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "sendUtterance error", e)
            null
        }
    }

    suspend fun verdict(requestId: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/verdict"
        val payload = json.encodeToString(
            VerdictRequest.serializer(),
            VerdictRequest(requestId, if (allow) "allow" else "deny")
        )
        val body = payload.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)

        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val resp = client.newCall(requestBuilder.build()).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "verdict error", e)
            false
        }
    }

    suspend fun say(text: String, agent: String, approvalId: String? = null): RouteResult? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/say"
        val payload = json.encodeToString(
            SayRequest.serializer(),
            SayRequest(text = text, agent = agent, approval = approvalId)
        )
        val body = payload.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)

        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val resp = client.newCall(requestBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()?.let { json.decodeFromString<RouteResult>(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "say error", e)
            null
        }
    }
}
