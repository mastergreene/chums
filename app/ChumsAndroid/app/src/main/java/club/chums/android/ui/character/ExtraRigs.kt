package club.chums.android.ui.character

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import club.chums.android.data.ChumsState

/** First-pass native Compose interpretations, not pixel-identical ports of upstream Swift rigs. */
@Composable
fun PeepsRig(state: ChumsState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "peeps")
    val blink by transition.animateFloat(
        initialValue = 1f, targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "blink"
    )
    Canvas(modifier.fillMaxSize()) {
        val unit = size.minDimension
        val centerY = size.height / 2f
        val iris = if (state == ChumsState.THINKING) -unit * .045f else 0f
        val eyeSize = unit * .18f
        val spread = unit * .23f
        val closed = state == ChumsState.SLEEP
        val h = if (closed) .09f else if (state == ChumsState.LISTENING) 1.12f else blink.coerceAtLeast(.5f)
        for (dx in listOf(-spread, spread)) {
            drawOval(
                color = Color.White,
                topLeft = Offset(size.width / 2 + dx - eyeSize / 2, centerY - eyeSize * h),
                size = androidx.compose.ui.geometry.Size(eyeSize, eyeSize * 2 * h)
            )
            if (!closed) drawCircle(
                color = Color(0xFF15151B),
                radius = eyeSize * .24f,
                center = Offset(size.width / 2 + dx, centerY + iris)
            )
        }
        if (state == ChumsState.APPROVAL || state == ChumsState.NEEDS_YOU) {
            drawCircle(Color(0xFFFFB74D), radius = unit * .025f, center = Offset(size.width / 2, centerY + unit * .22f))
        }
    }
}

/** Marker strokes that squint, shift and react to work, listening, approvals and sleep. */
@Composable
fun SquintRig(state: ChumsState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "squint")
    val motion by transition.animateFloat(
        initialValue = -.05f, targetValue = .05f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "motion"
    )
    Canvas(modifier.fillMaxSize()) {
        val u = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val expression = when (state) {
            ChumsState.SLEEP -> .02f
            ChumsState.THINKING -> -.10f
            ChumsState.APPROVAL, ChumsState.NEEDS_YOU -> -.16f
            ChumsState.LISTENING -> .22f
            ChumsState.WORKING, ChumsState.SUBAGENTS -> motion
            else -> .12f
        }
        for (side in listOf(-1f, 1f)) {
            val x = cx + side * u * .20f
            val p = Path().apply {
                moveTo(x - u * .09f, cy - u * .03f)
                quadraticBezierTo(x, cy - u * expression, x + u * .09f, cy - u * .03f)
            }
            drawPath(p, Color.White, style = Stroke(width = u * .045f, cap = StrokeCap.Round))
        }
        if (state == ChumsState.SPEAKING || state == ChumsState.DONE) {
            val smile = Path().apply {
                moveTo(cx - u * .08f, cy + u * .19f)
                quadraticBezierTo(cx, cy + u * .27f, cx + u * .08f, cy + u * .19f)
            }
            drawPath(smile, Color.White, style = Stroke(width = u * .025f, cap = StrokeCap.Round))
        }
    }
}
