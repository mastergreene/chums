package club.chums.android.ui.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import club.chums.android.data.ChumsState
import kotlin.math.cos
import kotlin.math.sin

object GarfieldPalette {
    val Orange = Color(0xFFFA8E1C)
    val ShadowOrange = Color(0xFFDE7407)
    val Cream = Color(0xFFFFF1D6)
    val SalmonPink = Color(0xFFFF6B6B)
    val Ink = Color(0xFF161413)
    val EyeWhite = Color(0xFFFFFFFC)
    val Pupil = Color(0xFF100F0D)
    val SoftHighlight = Color(0x40FFFFFF)
}

@Composable
fun GarfieldRig(
    state: ChumsState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "GarfieldInfinite")

    // Breathing / Gentle Bobbing
    val breathe by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == ChumsState.SLEEP) 2400 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GarfieldBreathe"
    )

    // Speaking mouth motion
    val mouthFlap by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GarfieldMouth"
    )

    // Ear twitch for listening
    val earTwitch by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GarfieldEar"
    )

    // Head tilt on thinking / approval
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            ChumsState.THINKING -> tilt.animateTo(-10f, tween(350))
            ChumsState.APPROVAL -> tilt.animateTo(6f, tween(300))
            else -> tilt.animateTo(0f, tween(350))
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + breathe
        val headRadius = size.minDimension * 0.32f

        // Draw Ears
        drawGarfieldEars(cx, cy, headRadius, state, earTwitch, tilt.value)

        // Draw Main Head Base
        drawRoundRect(
            color = GarfieldPalette.Orange,
            topLeft = Offset(cx - headRadius * 1.15f, cy - headRadius * 0.85f),
            size = Size(headRadius * 2.3f, headRadius * 1.8f),
            cornerRadius = CornerRadius(headRadius * 0.7f, headRadius * 0.7f)
        )

        // Head Stripes (Top crown stripes)
        drawTopStripes(cx, cy, headRadius)

        // Cheek Stripes
        drawCheekStripes(cx, cy, headRadius)

        // Chubby Muzzle / Cheeks (Cream colored)
        val cheekY = cy + headRadius * 0.25f
        drawCircle(
            color = GarfieldPalette.Cream,
            radius = headRadius * 0.42f,
            center = Offset(cx - headRadius * 0.35f, cheekY)
        )
        drawCircle(
            color = GarfieldPalette.Cream,
            radius = headRadius * 0.42f,
            center = Offset(cx + headRadius * 0.35f, cheekY)
        )

        // Eyes (Iconic big expressive ovals)
        drawGarfieldEyes(cx, cy, headRadius, state)

        // Nose (Cute pink oval in the center)
        drawOval(
            color = GarfieldPalette.SalmonPink,
            topLeft = Offset(cx - headRadius * 0.16f, cy + headRadius * 0.05f),
            size = Size(headRadius * 0.32f, headRadius * 0.24f)
        )

        // Whisker Dots
        val dotRadius = headRadius * 0.035f
        for (i in -1..1) {
            drawCircle(
                color = GarfieldPalette.Ink,
                radius = dotRadius,
                center = Offset(cx - headRadius * 0.38f + i * headRadius * 0.08f, cheekY - headRadius * 0.05f)
            )
            drawCircle(
                color = GarfieldPalette.Ink,
                radius = dotRadius,
                center = Offset(cx + headRadius * 0.38f + i * headRadius * 0.08f, cheekY - headRadius * 0.05f)
            )
        }

        // Whiskers (3 on each cheek)
        drawWhiskers(cx, cheekY, headRadius)

        // Mouth (Reacts to state: smug smile, talking, sleeping)
        drawGarfieldMouth(cx, cy, headRadius, state, mouthFlap)
    }
}

private fun DrawScope.drawGarfieldEars(
    cx: Float,
    cy: Float,
    r: Float,
    state: ChumsState,
    twitch: Float,
    tilt: Float
) {
    val earW = r * 0.5f
    val earH = r * 0.55f

    // Left Ear
    val leftPath = Path().apply {
        moveTo(cx - r * 0.95f, cy - r * 0.5f)
        lineTo(cx - r * 0.75f - if (state == ChumsState.LISTENING) twitch else 0f, cy - r * 1.15f)
        lineTo(cx - r * 0.35f, cy - r * 0.8f)
        close()
    }
    drawPath(leftPath, GarfieldPalette.Orange)
    // Left Inner Ear (Pink)
    val leftInner = Path().apply {
        moveTo(cx - r * 0.88f, cy - r * 0.55f)
        lineTo(cx - r * 0.73f, cy - r * 1.02f)
        lineTo(cx - r * 0.45f, cy - r * 0.78f)
        close()
    }
    drawPath(leftInner, GarfieldPalette.SalmonPink)

    // Right Ear
    val rightPath = Path().apply {
        moveTo(cx + r * 0.35f, cy - r * 0.8f)
        lineTo(cx + r * 0.75f + if (state == ChumsState.LISTENING) twitch else 0f, cy - r * 1.15f)
        lineTo(cx + r * 0.95f, cy - r * 0.5f)
        close()
    }
    drawPath(rightPath, GarfieldPalette.Orange)
    // Right Inner Ear (Pink)
    val rightInner = Path().apply {
        moveTo(cx + r * 0.45f, cy - r * 0.78f)
        lineTo(cx + r * 0.73f, cy - r * 1.02f)
        lineTo(cx + r * 0.88f, cy - r * 0.55f)
        close()
    }
    drawPath(rightInner, GarfieldPalette.SalmonPink)
}

private fun DrawScope.drawTopStripes(cx: Float, cy: Float, r: Float) {
    // 3 crown stripes
    val topY = cy - r * 0.85f
    for (offset in listOf(-0.25f, 0f, 0.25f)) {
        val path = Path().apply {
            moveTo(cx + offset * r - r * 0.08f, topY)
            lineTo(cx + offset * r, topY + r * 0.28f)
            lineTo(cx + offset * r + r * 0.08f, topY)
            close()
        }
        drawPath(path, GarfieldPalette.Ink)
    }
}

private fun DrawScope.drawCheekStripes(cx: Float, cy: Float, r: Float) {
    // Left cheek stripes
    val leftPath = Path().apply {
        moveTo(cx - r * 1.15f, cy - r * 0.15f)
        lineTo(cx - r * 0.85f, cy - r * 0.05f)
        lineTo(cx - r * 1.15f, cy + r * 0.05f)
        close()
    }
    drawPath(leftPath, GarfieldPalette.Ink)

    // Right cheek stripes
    val rightPath = Path().apply {
        moveTo(cx + r * 1.15f, cy - r * 0.15f)
        lineTo(cx + r * 0.85f, cy - r * 0.05f)
        lineTo(cx + r * 1.15f, cy + r * 0.05f)
        close()
    }
    drawPath(rightPath, GarfieldPalette.Ink)
}

private fun DrawScope.drawGarfieldEyes(
    cx: Float,
    cy: Float,
    r: Float,
    state: ChumsState
) {
    val eyeW = r * 0.55f
    val eyeH = r * 0.75f
    val eyeY = cy - r * 0.35f

    if (state == ChumsState.SLEEP) {
        // Closed sleeping eyes: curved contented arcs
        val leftArc = Path().apply {
            moveTo(cx - r * 0.55f, eyeY + eyeH * 0.5f)
            quadraticBezierTo(cx - r * 0.3f, eyeY + eyeH * 0.2f, cx - r * 0.05f, eyeY + eyeH * 0.5f)
        }
        val rightArc = Path().apply {
            moveTo(cx + r * 0.05f, eyeY + eyeH * 0.5f)
            quadraticBezierTo(cx + r * 0.3f, eyeY + eyeH * 0.2f, cx + r * 0.55f, eyeY + eyeH * 0.5f)
        }
        drawPath(leftArc, GarfieldPalette.Ink, style = Stroke(width = r * 0.06f, cap = StrokeCap.Round))
        drawPath(rightArc, GarfieldPalette.Ink, style = Stroke(width = r * 0.06f, cap = StrokeCap.Round))
        return
    }

    // Left Eye White
    drawOval(
        color = GarfieldPalette.EyeWhite,
        topLeft = Offset(cx - eyeW - r * 0.02f, eyeY),
        size = Size(eyeW, eyeH)
    )
    drawOval(
        color = GarfieldPalette.Ink,
        topLeft = Offset(cx - eyeW - r * 0.02f, eyeY),
        size = Size(eyeW, eyeH),
        style = Stroke(width = r * 0.04f)
    )

    // Right Eye White
    drawOval(
        color = GarfieldPalette.EyeWhite,
        topLeft = Offset(cx + r * 0.02f, eyeY),
        size = Size(eyeW, eyeH)
    )
    drawOval(
        color = GarfieldPalette.Ink,
        topLeft = Offset(cx + r * 0.02f, eyeY),
        size = Size(eyeW, eyeH),
        style = Stroke(width = r * 0.04f)
    )

    // Pupils
    val pupilW = eyeW * 0.32f
    val pupilH = eyeH * 0.45f
    val pupilY = eyeY + eyeH * 0.42f

    val pupilShiftX = when (state) {
        ChumsState.THINKING -> r * 0.08f
        ChumsState.WORKING -> 0f
        else -> 0f
    }

    drawOval(
        color = GarfieldPalette.Pupil,
        topLeft = Offset(cx - eyeW * 0.55f + pupilShiftX, pupilY),
        size = Size(pupilW, pupilH)
    )
    drawOval(
        color = GarfieldPalette.Pupil,
        topLeft = Offset(cx + eyeW * 0.25f + pupilShiftX, pupilY),
        size = Size(pupilW, pupilH)
    )

    // Iconic Half-Lidded Heavy Eyelids (Garfield's sleepy/smug trademark)
    val lidCover = when (state) {
        ChumsState.THINKING -> 0.45f
        ChumsState.WORKING -> 0.60f
        ChumsState.DONE -> 0.30f
        else -> 0.50f
    }
    drawRect(
        color = GarfieldPalette.Orange,
        topLeft = Offset(cx - eyeW - r * 0.04f, eyeY - r * 0.02f),
        size = Size(eyeW * 2f + r * 0.08f, eyeH * lidCover)
    )
    // Eyelid line
    drawLine(
        color = GarfieldPalette.Ink,
        start = Offset(cx - eyeW - r * 0.04f, eyeY + eyeH * lidCover),
        end = Offset(cx + eyeW + r * 0.04f, eyeY + eyeH * lidCover),
        strokeWidth = r * 0.045f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawWhiskers(cx: Float, cheekY: Float, r: Float) {
    val whiskerLen = r * 0.45f
    val stroke = Stroke(width = r * 0.025f, cap = StrokeCap.Round)

    // Left Whiskers
    drawLine(GarfieldPalette.Ink, Offset(cx - r * 0.7f, cheekY - r * 0.08f), Offset(cx - r * 0.7f - whiskerLen, cheekY - r * 0.16f), strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(GarfieldPalette.Ink, Offset(cx - r * 0.72f, cheekY), Offset(cx - r * 0.72f - whiskerLen * 1.1f, cheekY), strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(GarfieldPalette.Ink, Offset(cx - r * 0.7f, cheekY + r * 0.08f), Offset(cx - r * 0.7f - whiskerLen, cheekY + r * 0.16f), strokeWidth = stroke.width, cap = stroke.cap)

    // Right Whiskers
    drawLine(GarfieldPalette.Ink, Offset(cx + r * 0.7f, cheekY - r * 0.08f), Offset(cx + r * 0.7f + whiskerLen, cheekY - r * 0.16f), strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(GarfieldPalette.Ink, Offset(cx + r * 0.72f, cheekY), Offset(cx + r * 0.72f + whiskerLen * 1.1f, cheekY), strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(GarfieldPalette.Ink, Offset(cx + r * 0.7f, cheekY + r * 0.08f), Offset(cx + r * 0.7f + whiskerLen, cheekY + r * 0.16f), strokeWidth = stroke.width, cap = stroke.cap)
}

private fun DrawScope.drawGarfieldMouth(
    cx: Float,
    cy: Float,
    r: Float,
    state: ChumsState,
    mouthFlap: Float
) {
    val mouthY = cy + headRadius(r) * 0.45f

    if (state == ChumsState.SPEAKING) {
        // Animated speaking mouth
        val h = r * 0.25f * mouthFlap
        drawOval(
            color = GarfieldPalette.SalmonPink,
            topLeft = Offset(cx - r * 0.18f, mouthY),
            size = Size(r * 0.36f, h)
        )
        drawOval(
            color = GarfieldPalette.Ink,
            topLeft = Offset(cx - r * 0.18f, mouthY),
            size = Size(r * 0.36f, h),
            style = Stroke(width = r * 0.035f)
        )
    } else {
        // Classic Smug Cat Smile (:3 curve)
        val stroke = Stroke(width = r * 0.035f, cap = StrokeCap.Round)
        val leftSmile = Path().apply {
            moveTo(cx - r * 0.28f, mouthY)
            quadraticBezierTo(cx - r * 0.14f, mouthY + r * 0.12f, cx, mouthY + r * 0.02f)
        }
        val rightSmile = Path().apply {
            moveTo(cx, mouthY + r * 0.02f)
            quadraticBezierTo(cx + r * 0.14f, mouthY + r * 0.12f, cx + r * 0.28f, mouthY)
        }
        drawPath(leftSmile, GarfieldPalette.Ink, style = stroke)
        drawPath(rightSmile, GarfieldPalette.Ink, style = stroke)
    }
}

private fun headRadius(r: Float): Float = r
