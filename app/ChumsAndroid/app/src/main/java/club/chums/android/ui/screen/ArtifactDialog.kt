package club.chums.android.ui.screen

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import club.chums.android.data.Artifact
import club.chums.android.data.HubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/** Authenticated fetches happen outside WebView; generated pages never receive the hub token. */
@Composable
fun ArtifactDialog(artifact: Artifact, hub: HubClient, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var file by remember(artifact.id) { mutableStateOf<File?>(null) }
    var html by remember(artifact.id) { mutableStateOf<String?>(null) }
    var picture by remember(artifact.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(artifact.id) { mutableStateOf<String?>(null) }
    val type = artifact.view?.type ?: "image"
    LaunchedEffect(artifact.id) {
        try {
            when (type) {
                "html", "text" -> html = hub.fetchArtifact(artifact.id, "view").toString(Charsets.UTF_8)
                "pdf", "video", "doc" -> {
                    val bytes = hub.fetchArtifact(artifact.id, "source")
                    val extension = artifact.view?.name?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,12}")) }
                        ?: when (type) { "pdf" -> "pdf"; "video" -> "mp4"; else -> "bin" }
                    file = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "artifacts").apply { mkdirs() }
                        // Cache is bounded by age; a receiver may need a shared file after this dialog closes.
                        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86400000L }?.forEach { it.delete() }
                        File.createTempFile("result-", ".$extension", dir).also { it.writeBytes(bytes) }
                    }
                }
                else -> picture = withContext(Dispatchers.IO) {
                    val bytes = hub.fetchArtifact(artifact.id, "full")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Couldn't decode picture")
                }
            }
        } catch (e: Exception) { error = e.message ?: "Couldn't load result. Reconnect and open it again." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF15101F)).padding(16.dp).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth()) {
                Text(artifact.title, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            error?.let { Text(it, color = Color.White) }
            if (type == "html" || type == "text") Text("Interactive preview. External resources are disabled.", color = Color.White)
            if (type == "url") TextButton(onClick = {
                try {
                    val uri = Uri.parse(artifact.view?.url)
                    require(uri.scheme in setOf("https", "http"))
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) { error = "Couldn't open the page." }
            }) { Text("Open live page in browser") }
            when {
                html != null -> ContainedPage(html!!, Modifier.weight(1f).fillMaxWidth())
                picture != null -> ZoomPicture(picture!!, artifact.title, Modifier.weight(1f).fillMaxWidth())
                file != null && type == "pdf" -> PdfPages(file!!, Modifier.weight(1f).fillMaxWidth())
                file != null && type == "video" -> AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { VideoView(it).apply {
                        setVideoPath(file!!.absolutePath)
                        setMediaController(android.widget.MediaController(it).also { controller -> controller.setAnchorView(this) })
                        setOnErrorListener { _, _, _ -> error = "Couldn't play this video. Try opening the original file."; true }
                    } },
                    onRelease = { it.stopPlayback() }
                )
                file != null -> Text("Open the original in an app that supports this file.", color = Color.White)
                error == null -> CircularProgressIndicator()
            }
            if (file != null) TextButton(onClick = {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file!!)
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file!!.extension.lowercase()) ?: "application/octet-stream"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open result"))
                } catch (_: Exception) { error = "No installed app can open this file." }
            }) { Text("Open original file") }
        }
    }
}

@Composable
private fun ZoomPicture(bitmap: Bitmap, title: String, modifier: Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Box(modifier.clipToBounds().transformable(transform)) {
        Image(bitmap.asImageBitmap(), title, Modifier.fillMaxSize().graphicsLayer(
            scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
    }
}

@Suppress("SetJavaScriptEnabled")
@Composable
private fun ContainedPage(html: String, modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.blockNetworkLoads = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            }
            // No hub origin, credentials, local-file access, external resources or navigation.
            val policy = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; form-action 'none'; base-uri 'none'\">"
            loadDataWithBaseURL("https://chums-artifact.invalid/", policy + html, "text/html", "UTF-8", null)
        }
    }, onRelease = { it.stopLoading(); it.destroy() })
}

@Composable
private fun PdfPages(file: File, modifier: Modifier) {
    var count by remember(file) { mutableIntStateOf(0) }
    var failure by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        try { count = withContext(Dispatchers.IO) { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount } } }
        catch (_: Exception) { failure = "Couldn't preview this PDF. Open the original file below." }
    }
    LazyColumn(modifier) {
        failure?.let { item { Text(it, color = Color.White) } }
        items((0 until count).toList()) { number ->
            var bitmap by remember(file, number) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(file, number) {
                try {
                    bitmap = withContext(Dispatchers.IO) {
                        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                            renderer.openPage(number).use { page ->
                                val width = 1080
                                val height = (page.height.toFloat() / page.width * width).toInt().coerceIn(1, 4096)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                } catch (_: Exception) { failure = "Couldn't render a PDF page. Open the original below." }
            }
            bitmap?.let { Image(it.asImageBitmap(), "Page ${number + 1}", Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
        }
    }
}
