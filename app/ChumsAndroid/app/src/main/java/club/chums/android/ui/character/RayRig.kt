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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object RayPalette {
    val Gold = Color(0xFFF2AE08)
    val Cream = Color(0xFFF9ECD5)
    val Ink = Color(0xFF040201)
    val Pink = Color(0xFFE64E4B)
    val DarkPanel = Color(0xFF0E0C11)
}

@Composable
fun RayRig(
    state: ChumsState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RayInfinite")
    
    // Wave animation running continuously
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    ChumsState.WORKING -> 1200
                    ChumsState.LISTENING -> 1600
                    ChumsState.SPEAKING -> 1800
                    ChumsState.SLEEP -> 4000
                    else -> 2400
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RayWave"
    )

    // Gentle bounce / pulse
    val bounce by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RayBounce"
    )

    // Speaking mouth animation
    val mouthScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RayMouth"
    )

    // Tilt angle for thinking
    val tiltAngle = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            ChumsState.THINKING -> tiltAngle.animateTo(-12f, tween(400))
            ChumsState.APPROVAL -> tiltAngle.animateTo(8f, tween(300))
            else -> tiltAngle.animateTo(0f, tween(400))
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + if (state == ChumsState.DONE) bounce * 2 else bounce
        val radius = size.minDimension * 0.22f

        // Draw 10 wavy sun rays
        val numRays = 10
        val rayLength = radius * 1.5f
        for (i in 0 until numRays) {
            val baseAngle = (i * 2f * PI.toFloat() / numRays) + (tiltAngle.value * PI.toFloat() / 180f)
            val path = Path()

            val startX = cx + cos(baseAngle) * radius * 0.9f
            val startY = cy + sin(baseAngle) * radius * 0.9f
            path.moveTo(startX, startY)

            val segments = 16
            for (s in 1..segments) {
                val t = s.toFloat() / segments
                val dist = radius * 0.9f + t * rayLength
                val waveAmp = when (state) {
                    ChumsState.WORKING -> 22f
                    ChumsState.LISTENING -> 16f
                    ChumsState.SLEEP -> 6f
                    else -> 12f
                }
                val waveOffset = sin(wavePhase + (i * 1.2f) + (t * 4f)) * waveAmp * (1f - t * 0.2f)
                
                // Perpendicular vector for wave displacement
                val perpX = -sin(baseAngle) * waveOffset
                val perpY = cos(baseAngle) * waveOffset

                val px = cx + cos(baseAngle) * dist + perpX
                val py = cy + sin(baseAngle) * dist + perpY
                path.lineTo(px, py)
            }

            drawPath(
                path = path,
                color = RayPalette.Gold,
                style = Stroke(
                    width = radius * 0.18f,
                    cap = StrokeCap.Round
                )
            )
        }

        // Draw center round body
        drawCircle(
            color = RayPalette.Gold,
            radius = radius,
            center = Offset(cx, cy)
        )

        // Draw Eyes and Face based on State
        drawFace(cx, cy, radius, state, mouthScale)
    }
}

private fun DrawScope.drawFace(
    cx: Float,
    cy: Float,
    radius: Float,
    state: ChumsState,
    mouthScale: Float
) {
    val eyeRadius = radius * 0.24f
    val eyeSpacing = radius * 0.32f
    val eyeY = cy - radius * 0.15f

    when (state) {
        ChumsState.SLEEP -> {
            // Sleeping eyes: horizontal dashes
            drawLine(
                color = RayPalette.Ink,
                start = Offset(cx - eyeSpacing - eyeRadius * 0.8f, eyeY),
                end = Offset(cx - eyeSpacing + eyeRadius * 0.8f, eyeY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = RayPalette.Ink,
                start = Offset(cx + eyeSpacing - eyeRadius * 0.8f, eyeY),
                end = Offset(cx + eyeSpacing + eyeRadius * 0.8f, eyeY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            // Small o mouth for snoring/breathing
            drawCircle(
                color = RayPalette.Ink,
                radius = radius * 0.08f,
                center = Offset(cx, cy + radius * 0.32f)
            )
        }

        ChumsState.DONE -> {
            // Happy happy curved eyes: ◠ ◠
            val leftEyePath = Path().apply {
                moveTo(cx - eyeSpacing - eyeRadius * 0.7f, eyeY + eyeRadius * 0.3f)
                cubicTo(
                    cx - eyeSpacing - eyeRadius * 0.7f, eyeY - eyeRadius * 0.8f,
                    cx - eyeSpacing + eyeRadius * 0.7f, eyeY - eyeRadius * 0.8f,
                    cx - eyeSpacing + eyeRadius * 0.7f, eyeY + eyeRadius * 0.3f
                )
            }
            val rightEyePath = Path().apply {
                moveTo(cx + eyeSpacing - eyeRadius * 0.7f, eyeY + eyeRadius * 0.3f)
                cubicTo(
                    cx + eyeSpacing - eyeRadius * 0.7f, eyeY - eyeRadius * 0.8f,
                    cx + eyeSpacing + eyeRadius * 0.7f, eyeY - eyeRadius * 0.8f,
                    cx + eyeSpacing + eyeRadius * 0.7f, eyeY + eyeRadius * 0.3f
                )
            }
            drawPath(leftEyePath, RayPalette.Ink, style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(rightEyePath, RayPalette.Ink, style = Stroke(width = 7f, cap = StrokeCap.Round))

            // Big open happy smile D
            val smilePath = Path().apply {
                moveTo(cx - radius * 0.28f, cy + radius * 0.22f)
                lineTo(cx + radius * 0.28f, cy + radius * 0.22f)
                cubicTo(
                    cx + radius * 0.28f, cy + radius * 0.55f,
                    cx - radius * 0.28f, cy + radius * 0.55f,
                    cx - radius * 0.28f, cy + radius * 0.22f
                )
            }
            drawPath(smilePath, RayPalette.Ink)
        }

        ChumsState.BLOCKED, ChumsState.ERROR -> {
            // Angry brows and eyes
            drawLine(
                color = RayPalette.Ink,
                start = Offset(cx - eyeSpacing - eyeRadius, eyeY - eyeRadius * 0.6f),
                end = Offset(cx - eyeSpacing + eyeRadius, eyeY - eyeRadius * 0.2f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = RayPalette.Ink,
                start = Offset(cx + eyeSpacing + eyeRadius, eyeY - eyeRadius * 0.6f),
                end = Offset(cx + eyeSpacing - eyeRadius, eyeY - eyeRadius * 0.2f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawCircle(RayPalette.Cream, eyeRadius, Offset(cx - eyeSpacing, eyeY))
            drawCircle(RayPalette.Cream, eyeRadius, Offset(cx + eyeSpacing, eyeY))
            drawCircle(RayPalette.Ink, eyeRadius * 0.5f, Offset(cx - eyeSpacing, eyeY))
            drawCircle(RayPalette.Ink, eyeRadius * 0.5f, Offset(cx + eyeSpacing, eyeY))

            // Zigzag mouth
            val zigPath = Path().apply {
                moveTo(cx - radius * 0.25f, cy + radius * 0.3f)
                lineTo(cx - radius * 0.1f, cy + radius * 0.25f)
                lineTo(cx + radius * 0.05f, cy + radius * 0.35f)
                lineTo(cx + radius * 0.25f, cy + radius * 0.28f)
            }
            drawPath(zigPath, RayPalette.Ink, style = Stroke(width = 6f, cap = StrokeCap.Round))
        }

        else -> {
            // Normal big cream whites
            drawCircle(RayPalette.Cream, eyeRadius, Offset(cx - eyeSpacing, eyeY))
            drawCircle(RayPalette.Cream, eyeRadius, Offset(cx + eyeSpacing, eyeY))

            // Pupils position based on state
            val pupilOffset = when (state) {
                ChumsState.THINKING -> Offset(eyeRadius * 0.35f, -eyeRadius * 0.35f) // look up-right
                ChumsState.WORKING -> Offset(0f, eyeRadius * 0.2f) // look down focused
                ChumsState.LISTENING -> Offset(0f, 0f) // wide open center
                else -> Offset(0f, 0f)
            }

            val pupilRadius = if (state == ChumsState.LISTENING) eyeRadius * 0.65f else eyeRadius * 0.52f
            drawCircle(RayPalette.Ink, pupilRadius, Offset(cx - eyeSpacing + pupilOffset.x, eyeY + pupilOffset.y))
            drawCircle(RayPalette.Ink, pupilRadius, Offset(cx + eyeSpacing + pupilOffset.x, eyeY + pupilOffset.y))

            // Mouth
            when (state) {
                ChumsState.LISTENING -> {
                    // Surprised open O
                    drawCircle(
                        color = RayPalette.Ink,
                        radius = radius * 0.16f,
                        center = Offset(cx, cy + radius * 0.3f)
                    )
                }
                ChumsState.SPEAKING -> {
                    // Talking mouth, animating open and shut
                    val mouthW = radius * 0.3f
                    val mouthH = radius * 0.25f * mouthScale
                    drawRoundRect(
                        color = RayPalette.Ink,
                        topLeft = Offset(cx - mouthW / 2f, cy + radius * 0.22f),
                        size = Size(mouthW, mouthH),
                        cornerRadius = CornerRadius(mouthW / 2f, mouthH / 2f)
                    )
                }
                ChumsState.THINKING -> {
                    // Small inquisitive dot / mouth
                    drawCircle(
                        color = RayPalette.Ink,
                        radius = radius * 0.08f,
                        center = Offset(cx, cy + radius * 0.3f)
                    )
                }
                ChumsState.APPROVAL, ChumsState.NEEDS_YOU -> {
                    // Alert small circle
                    drawCircle(
                        color = RayPalette.Ink,
                        radius = radius * 0.12f,
                        center = Offset(cx, cy + radius * 0.3f)
                    )
                }
                else -> {
                    // Friendly calm smile: ‿
                    val smilePath = Path().apply {
                        moveTo(cx - radius * 0.22f, cy + radius * 0.25f)
                        cubicTo(
                            cx - radius * 0.15f, cy + radius * 0.42f,
                            cx + radius * 0.15f, cy + radius * 0.42f,
                            cx + radius * 0.22f, cy + radius * 0.25f
                        )
                    }
                    drawPath(
                        path = smilePath,
                        color = RayPalette.Ink,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
