package club.chums.android.ui.character

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import club.chums.android.data.ChumsState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

val originalCharacters = listOf("noodle", "stretch", "peeps", "squint", "ray", "tangle", "crunch", "nana")

/** Local, live character renderer. No hub URL, token or JavaScript/Android bridge enters this page. */
@Composable
fun OriginalCharacter(id: String, state: ChumsState, modifier: Modifier = Modifier,
    caption: String = "", tools: List<String> = emptyList(), subagents: Int = 0,
    eventId: String = "", kind: String = "", toolId: String = "", toolFailed: Boolean = false) {
    val payload = JSONObject().put("id", id.takeIf { it in originalCharacters } ?: "noodle")
        .put("state", state.name.lowercase()).put("caption", caption).put("tools", JSONArray(tools))
        .put("subagents", subagents).put("eventId", eventId).put("kind", kind).put("toolId", toolId).put("toolFailed", toolFailed).toString()
    AndroidView(modifier = modifier, factory = { LiveCharacterView(it) }, update = {
        it.contentDescription = "$id, ${state.name.lowercase()}"
        it.update(payload)
    }, onRelease = { it.evaluateJavascript("window.ChumsCharacter?.dispose()", null); it.stopLoading(); it.destroy() })
}

@SuppressLint("SetJavaScriptEnabled")
private class LiveCharacterView(context: Context) : WebView(context) {
    private var ready = false
    private var payload = "{}"
    private var lastSent = ""
    init {
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.setSupportZoom(false)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                if (request.url.host == "appassets.androidplatform.net" && request.url.path?.startsWith("/assets/renderer/") == true)
                    return loader.shouldInterceptRequest(request.url) ?: denied()
                return denied()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
            override fun onPageFinished(view: WebView?, url: String?) { ready = true; update(payload) }
        }
        loadUrl("https://appassets.androidplatform.net/assets/renderer/index.html")
    }
    private fun denied() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    fun update(value: String) {
        payload = value
        if (ready && lastSent != value) {
            lastSent = value
            evaluateJavascript("window.ChumsCharacter && window.ChumsCharacter.set($value)", null)
        }
    }
}
