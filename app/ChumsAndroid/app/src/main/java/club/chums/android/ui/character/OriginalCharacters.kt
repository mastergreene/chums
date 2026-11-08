package club.chums.android.ui.character

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import club.chums.android.data.ChumsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

val originalCharacters = listOf("noodle", "stretch", "peeps", "squint", "ray", "tangle", "crunch", "nana")

/** Original upstream artwork, not replacement drawings. Preview loops are not live rig ports. */
@Composable
fun OriginalCharacter(id: String, state: ChumsState, modifier: Modifier = Modifier, docked: Boolean = false) {
    val character = id.takeIf { it in originalCharacters } ?: "noodle"
    var drawable by remember(character) { mutableStateOf<Drawable?>(null) }
    AndroidView(modifier = modifier, factory = { OriginalCharacterView(it) }, update = { view ->
        view.docked = docked
        view.alpha = if (state == ChumsState.SLEEP || state == ChumsState.RECONNECTING) 0.45f else 1f
        view.contentDescription = "$character, ${state.name.lowercase()}"
        view.artwork = drawable
    }, onRelease = { it.artwork = null })
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(character) {
        drawable = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 28) {
                val bytes = context.assets.open("previews/$character.webp").use { it.readBytes() }
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            } else context.assets.open("avatars/avatar-$character.png").use { Drawable.createFromStream(it, character) }
        }
    }
}

private class OriginalCharacterView(context: Context) : View(context) {
    var docked = false
        set(value) { field = value; invalidate() }
    var artwork: Drawable? = null
        set(value) {
            if (field === value) return
            if (Build.VERSION.SDK_INT >= 28) (field as? AnimatedImageDrawable)?.stop()
            field?.callback = null
            field = value
            value?.callback = this
            if (Build.VERSION.SDK_INT >= 28) (value as? AnimatedImageDrawable)?.start()
            invalidate()
        }
    override fun verifyDrawable(who: Drawable) = who === artwork || super.verifyDrawable(who)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = artwork ?: return
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        if (docked && Build.VERSION.SDK_INT >= 28) {
            // The upstream media includes its watch shell. In the chat dock show only its central stage.
            canvas.scale(width / 230f, height / 214f)
            canvas.translate(-30f, -85f)
            image.setBounds(0, 0, 300, 334)
        } else image.setBounds(0, 0, width, height)
        image.draw(canvas)
        canvas.restore()
    }
    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 28) (artwork as? AnimatedImageDrawable)?.stop()
        super.onDetachedFromWindow()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= 28) (artwork as? AnimatedImageDrawable)?.start()
    }
}
