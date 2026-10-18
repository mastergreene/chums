package club.chums.android.ui.character

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import club.chums.android.data.ChumsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

// Garfield: the orange tabby, loaf build, from the "Wearable Cat Character" model sheet. Every shape is the sheet's,
// on its 200-unit grid; the motion follows its sheet 04 (timings, easings, how often each move may play).

private object Tabby {
    val Fur = Color(0xFFF5902F)
    val Stripe = Color(0xFFB9580F)
    val Cream = Color(0xFFFFF4E4)
    val Pupil = Color(0xFF120B07)
    val Nose = Color(0xFFFF8DA6)
    val Mouth = Color(0xFF5E2B0B)
    val PorchBlue = Color(0xFF3DB2FF)                  // house alerts only, never on the cat
    val Void = Color(0xFF000000)
}

private val Settle = CubicBezierEasing(.2f, .8f, .2f, 1f)
private val Sine = CubicBezierEasing(.45f, 0f, .55f, 1f)

private val parsed = HashMap<String, Path>()
/// An SVG path string as a Compose Path (each string parsed once).
private fun p(d: String): Path = parsed.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
/// Two paths with the same commands, `t` of the way from `a` to `b` (the ears rise and fall this way).
private fun lerpPath(a: String, b: String, t: Float): Path {
    if (t <= 0f) return p(a)
    if (t >= 1f) return p(b)
    val to = NUMBER.findAll(b).map { it.value.toFloat() }.toList()
    var i = 0
    val d = NUMBER.replace(a) { m -> val from = m.value.toFloat(); (from + (to[i++] - from) * t).toString() }
    return PathParser().parsePathString(d).toPath()
}

// ----- the sheet's shapes -----
private const val BODY = "M60,178 L140,178 Q152,178 151,164 L146,128 Q142,104 100,104 Q58,104 54,128 L49,164 Q48,178 60,178 Z"
private const val BODY_STRIPES = "M55,134 Q63,135 66,141 M53,150 Q61,151 64,157 M145,134 Q137,135 134,141 M147,150 Q139,151 136,157"
private const val BIB = "M100,112 Q120,120 116,146 Q110,168 100,170 Q90,168 84,146 Q80,120 100,112 Z"
private const val HEAD = "M100,38 C134,38 152,56 152,80 C152,104 130,118 100,118 C70,118 48,104 48,80 C48,56 66,38 100,38 Z"
private const val FACE_MARKS = "M86,56 L90,46 L100,54 L110,46 L114,56 M49,76 L59,78 M49,86 L59,86 M151,76 L141,78 M151,86 L141,86"
private const val WHISKERS = "M62,99 L36,95 M62,104 L36,108 M138,99 L164,95 M138,104 L164,108"
private const val NOSE = "M95.5,96 L104.5,96 L100,101.5 Z"
private const val MOUTH_W = "M100,101.5 L100,104.5 M93.5,104 Q96.5,108 100,104.5 Q103.5,108 106.5,104"
private const val MOUTH_FLAT = "M95,105 Q100,103 105,105"
private const val MOUTH_OPEN = "M93,103 Q100,116 107,103 Z"
private const val HAPPY_EYES = "M68,84 Q80,68 92,84 M108,84 Q120,68 132,84"
private const val SLEEP_EYES = "M69,81 Q80,90 91,81 M109,81 Q120,90 131,81"

private const val EAR_L = "M52,66 L58,26 Q60,16 68,22 L92,42 Z"
private const val EAR_L_IN = "M60,56 L63,32 Q64,27 68,30 L82,43 Z"
private const val EAR_R = "M148,66 L142,26 Q140,16 132,22 L108,42 Z"
private const val EAR_R_IN = "M140,56 L137,32 Q136,27 132,30 L118,43 Z"
private const val EAR_L_UP = "M52,64 L56,18 Q58,8 66,14 L92,42 Z"
private const val EAR_L_UP_IN = "M60,54 L62,28 Q63,21 67,24 L82,43 Z"
private const val EAR_R_UP = "M148,64 L144,18 Q142,8 134,14 L108,42 Z"
private const val EAR_R_UP_IN = "M140,54 L138,28 Q137,21 133,24 L118,43 Z"
private const val EAR_L_LOW = "M52,72 L30,46 Q24,38 34,38 L88,44 Z"           // asleep, and the basic-mode outline
private const val EAR_L_LOW_IN = "M58,64 L40,46 Q36,42 41,42 L76,46 Z"
private const val EAR_R_LOW = "M148,72 L170,46 Q176,38 166,38 L112,44 Z"
private const val EAR_R_LOW_IN = "M142,64 L160,46 Q164,42 159,42 L124,46 Z"

private const val TAIL = "M140,170 C172,170 180,140 168,116"
private const val TAIL_TIP = "M172,127 Q171,121 168,116"
private const val TAIL_HOOK = "M142,170 C162,170 168,150 168,118 C168,100 172,92 180,92"      // a new message
private const val TAIL_HOOK_TIP = "M169,104 C169,97 173,92 180,92"
private const val TAIL_UP = "M142,170 C162,170 168,150 168,120 L168,92"                       // alert: straight up
private const val TAIL_UP_TIP = "M168,104 L168,92"

private const val HOUSE = "M90,27 L100,18 L110,27 L110,36 L90,36 Z"
private const val HOUSE_DOOR = "M97.5,36 L97.5,30 L102.5,30 L102.5,36"
private const val BUBBLE_TAIL = "M152,53 L149,63 L160,55 Z"

// basic mode: the same cat as an outline, at low power
private const val B_TAIL = "M142,172 C162,176 176,176 184,168"
private const val B_BIB = "M100,118 Q120,124 116,146 Q110,168 100,170 Q90,168 84,146 Q80,124 100,118"
private const val B_EARS = "M52,72 L30,46 Q24,38 34,38 L88,44 M148,72 L170,46 Q176,38 166,38 L112,44"
private const val B_WHISKERS = "M66,99 L42,96 M66,104 L42,107 M134,99 L158,96 M134,104 L158,107"
private const val B_NOSE = "M96.5,97 L103.5,97 L100,101 Z"
private const val B_MOUTH = "M95,106 L105,106"

private const val TAIL_BANDS_W = 14f
private val tailBands = PathEffect.dashPathEffect(floatArrayOf(0f, 16f, 5f, 8f, 5f, 8f, 5f, 8f, 5f, 200f))

/// The sheet's states, which several of the app's states share.
private enum class Pose { IDLE, LISTENING, THINKING, MESSAGE, ALERT, BASIC }

private fun poseFor(state: ChumsState) = when (state) {
    ChumsState.IDLE, ChumsState.DONE -> Pose.IDLE
    ChumsState.LISTENING -> Pose.LISTENING
    ChumsState.THINKING, ChumsState.WORKING, ChumsState.SUBAGENTS -> Pose.THINKING
    ChumsState.SPEAKING -> Pose.MESSAGE
    ChumsState.APPROVAL, ChumsState.NEEDS_YOU, ChumsState.BLOCKED, ChumsState.ERROR, ChumsState.DIZZY -> Pose.ALERT
    ChumsState.SLEEP -> Pose.BASIC
}

/// One eye's numbers, left eye; the right is the same 40 units over unless its pupil looks the other way.
private data class Eyes(val white: Float, val pupil: Float, val px: Float, val py: Float, val rpx: Float, val glint: Float, val gx: Float, val gy: Float,
                        val small: Float = 0f, val sdx: Float = 8f, val sdy: Float = 9.5f)

private fun eyesFor(pose: Pose) = when (pose) {
    Pose.LISTENING -> Eyes(15.5f, 11f, 80.5f, 81f, 120.5f, 3.8f, 75f, 75f, 1.6f, 10.5f, 11f)
    Pose.THINKING -> Eyes(13f, 8f, 85f, 75f, 125f, 2.6f, 82f, 72f)
    Pose.ALERT -> Eyes(14f, 6f, 82f, 75f, 118f, 2f, 80f, 73f)
    else -> Eyes(13f, 8.5f, 81.5f, 81.5f, 121.5f, 3.2f, 77f, 76f, 1.4f)
}

private fun reducedMotion(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun GarfieldRig(state: ChumsState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { reducedMotion(context) }
    val pose = poseFor(state)

    // ----- what changes between poses, eased (a 200 ms crossfade instead when motion is reduced) -----
    val eyes = eyesFor(pose)
    val eyeSpec = if (still) tween<Float>(200) else spring(dampingRatio = 0.45f, stiffness = 500f)   // the sheet's 1.25 overshoot
    val white by animateFloatAsState(eyes.white, eyeSpec, label = "white")
    val pupil by animateFloatAsState(eyes.pupil, eyeSpec, label = "pupil")
    val px by animateFloatAsState(eyes.px, tween(if (still) 0 else 200, easing = Settle), label = "px")
    val py by animateFloatAsState(eyes.py, tween(if (still) 0 else 200, easing = Settle), label = "py")
    val rpx by animateFloatAsState(eyes.rpx, tween(if (still) 0 else 200, easing = Settle), label = "rpx")
    val earsUp by animateFloatAsState(if (pose == Pose.LISTENING || pose == Pose.MESSAGE || pose == Pose.ALERT) 1f else 0f,
        tween(if (still) 0 else if (pose == Pose.ALERT) 150 else 180, easing = Settle), label = "ears")
    val rightEarUp by animateFloatAsState(if (pose == Pose.THINKING) 1f else 0f, tween(if (still) 0 else 200, easing = Settle), label = "rightEar")

    // ----- idle: blinks at random every 4 to 9 s (1 in 5 a double), an ear flick at most once in 45 s -----
    val blink = remember { Animatable(1f) }                     // the eyes' height, 1 open
    val flick = remember { Animatable(0f) }                     // the left ear's turn, degrees
    LaunchedEffect(pose, still) {
        if (pose != Pose.IDLE || still) return@LaunchedEffect
        launch {
            while (isActive) {
                delay(Random.nextLong(4000, 9000))
                repeat(if (Random.nextInt(5) == 0) 2 else 1) {
                    blink.animateTo(0.08f, tween(80, easing = Sine)); blink.animateTo(1f, tween(80, easing = Sine)); delay(120)
                }
            }
        }
        while (isActive) {
            delay(Random.nextLong(20_000, 45_000))
            flick.animateTo(-16f, tween(70)); flick.animateTo(5f, tween(70)); flick.animateTo(0f, spring(dampingRatio = 0.5f))
        }
    }

    // ----- thinking: the rim arc laps in 1.4 s, slowing to 2.4 s after 6 s; the pupils come back to centre then -----
    var thinkingLong by remember { mutableStateOf(false) }
    LaunchedEffect(pose) { thinkingLong = false; if (pose == Pose.THINKING) { delay(6000); thinkingLong = true } }
    val arc = remember { Animatable(0f) }
    LaunchedEffect(pose, thinkingLong, still) {
        if (pose != Pose.THINKING || still) return@LaunchedEffect
        while (isActive) { arc.snapTo(arc.value % 360f); arc.animateTo(arc.value + 360f, tween(if (thinkingLong) 2400 else 1400, easing = LinearEasing)) }
    }
    val driftA = remember { Animatable(0f) }
    LaunchedEffect(pose, thinkingLong, still) {
        if (pose != Pose.THINKING || thinkingLong || still) { driftA.snapTo(0f); return@LaunchedEffect }
        while (isActive) { driftA.animateTo(1f, tween(1500, easing = Sine)); driftA.animateTo(0f, tween(1500, easing = Sine)) }
    }
    val drift = driftA.value

    // ----- listening: two orange rings pulse out, 0.8 s apart -----
    val ringA = remember { Animatable(0f) }
    LaunchedEffect(pose, still) {
        if (pose != Pose.LISTENING || still) return@LaunchedEffect
        while (isActive) { ringA.snapTo(0f); ringA.animateTo(1f, tween(1600, easing = LinearEasing)) }
    }
    val ring = ringA.value

    // ----- a new message: one hop (at most once in 10 minutes), the bubble pops; the mouth talks while it speaks -----
    val hop = remember { Animatable(0f) }
    val bubble = remember { Animatable(0f) }
    var lastHopAt by remember { mutableLongStateOf(0L) }
    var mouthOpen by remember { mutableStateOf(true) }
    LaunchedEffect(pose, still) {
        if (pose != Pose.MESSAGE) { bubble.snapTo(0f); return@LaunchedEffect }
        if (still) { bubble.snapTo(1f); return@LaunchedEffect }
        launch {
            bubble.snapTo(0f); delay(120)
            bubble.animateTo(1.15f, tween(110)); bubble.animateTo(1f, tween(90)); delay(2000); bubble.animateTo(0f, tween(300))
        }
        launch { while (isActive) { delay(Random.nextLong(140, 220)); mouthOpen = !mouthOpen } }
        val now = System.currentTimeMillis()
        if (now - lastHopAt > 10 * 60_000) {
            lastHopAt = now
            hop.animateTo(-10f, tween(220, easing = CubicBezierEasing(.3f, 1.4f, .5f, 1f)))
            hop.animateTo(0f, tween(160)); hop.animateTo(-4f, tween(100)); hop.animateTo(0f, tween(100))
        }
    }

    // ----- alert: the porch-blue rim pulses twice, then holds at 60%; again every 30 s, three times at most -----
    val alarm = remember { Animatable(0f) }
    LaunchedEffect(pose, still) {
        if (pose != Pose.ALERT) { alarm.snapTo(0f); return@LaunchedEffect }
        if (still) { alarm.snapTo(0.6f); return@LaunchedEffect }
        repeat(4) { round ->
            repeat(2) { alarm.animateTo(1f, tween(100)); alarm.animateTo(0.2f, tween(150)) }
            alarm.animateTo(0.6f, tween(120))
            if (round < 3) delay(30_000)
        }
    }

    // ----- basic mode breathes between 45% and 58%; leaving it, the cat wakes up -----
    val breatheA = remember { Animatable(0.45f) }
    LaunchedEffect(pose, still) {
        if (pose != Pose.BASIC || still) return@LaunchedEffect
        while (isActive) { breatheA.animateTo(0.58f, tween(2500, easing = Sine)); breatheA.animateTo(0.45f, tween(2500, easing = Sine)) }
    }
    val wake = remember { Animatable(1f) }                      // 0..1 through the 1.2 s wake-up; 1 awake
    var asleepSince by remember { mutableLongStateOf(0L) }
    var wokeOnce by remember { mutableStateOf(false) }
    var longWake by remember { mutableStateOf(false) }
    LaunchedEffect(pose == Pose.BASIC) {
        if (pose == Pose.BASIC) { asleepSince = System.currentTimeMillis(); return@LaunchedEffect }
        if (asleepSince == 0L) return@LaunchedEffect            // it never slept
        // the full sequence on the first wake, or after 2 h asleep; otherwise a 250 ms fade straight in
        longWake = !still && (!wokeOnce || System.currentTimeMillis() - asleepSince > 2 * 3600_000)
        wokeOnce = true
        wake.snapTo(0f)
        wake.animateTo(1f, tween(if (longWake) 1200 else if (still) 200 else 250, easing = LinearEasing))
    }
    val waking = wake.value < 1f
    val fullWake = waking && longWake

    Canvas(modifier = modifier.fillMaxSize().semantics { contentDescription = "Garfield, ${state.name.lowercase().replace('_', ' ')}" }) {
        val k = size.minDimension / 200f
        withTransform({
            translate((size.width - 200f * k) / 2f, (size.height - 200f * k) / 2f)
            scale(k, k, Offset.Zero)
        }) {
            if (pose == Pose.BASIC) { drawBasic(breatheA.value); return@withTransform }

            // the wake-up: brightness, ears rising and the eyes' "first try" are all read from where it is in 1.2 s
            val t = wake.value * 1200f
            val alpha = if (!waking) 1f else if (!fullWake) wake.value
                else when { t < 300 -> 0.35f * t / 300f; t < 1000 -> 0.35f + 0.45f * (t - 300) / 700f; else -> 0.8f + 0.2f * (t - 1000) / 200f }
            val earsLow = if (fullWake) (1f - ((t - 300f) / 300f).coerceIn(0f, 1f)) else 0f
            val lids = if (!fullWake) blink.value else when { t < 600 -> 0f; t < 760 -> 0.55f; t < 840 -> 0.3f; else -> 1f }
            val rise = if (fullWake) 5f * (1f - (t / 1200f)) else 0f

            // rims around the cat (behind it)
            when (pose) {
                Pose.LISTENING -> if (!still) {
                    for (phase in listOf(ring, (ring + 0.5f) % 1f)) {
                        val r = 95f * (0.93f + 0.09f * phase)
                        drawCircle(Tabby.Fur.copy(alpha = 0.9f * (1f - phase)), r, Offset(100f, 100f), style = Stroke(3f))
                    }
                }
                Pose.THINKING -> drawArc(Tabby.Fur, arc.value - 90f, 42f, false, Offset(5f, 5f), Size(190f, 190f), style = Stroke(4f, cap = StrokeCap.Round))
                Pose.ALERT -> drawCircle(Tabby.PorchBlue.copy(alpha = alarm.value), 95f, Offset(100f, 100f), style = Stroke(6f))
                else -> {}
            }

            translate(0f, hop.value + rise) {
                drawCat(pose, alpha, earsUp, rightEarUp, earsLow, flick.value, lids, white, pupil,
                    if (pose == Pose.THINKING && !thinkingLong && !still) px + 1.5f * drift else if (pose == Pose.THINKING && thinkingLong) 81.5f else px,
                    if (pose == Pose.THINKING && !thinkingLong && !still) py - drift else if (pose == Pose.THINKING && thinkingLong) 81.5f else py,
                    if (pose == Pose.THINKING && thinkingLong) 121.5f else rpx + if (pose == Pose.THINKING && !still) 1.5f * drift else 0f,
                    eyes, mouthOpen, fullWake && t < 600)
            }

            if (pose == Pose.MESSAGE && bubble.value > 0f) {
                scale(bubble.value, bubble.value, Offset(148f, 56f)) {
                    val a = bubble.value.coerceAtMost(1f)
                    drawRoundRect(Tabby.Cream.copy(alpha = a), Offset(148f, 36f), Size(27f, 20f), CornerRadius(8f))
                    drawPath(p(BUBBLE_TAIL), Tabby.Cream.copy(alpha = a))
                    for (x in listOf(155f, 161.5f, 168f)) drawCircle(Tabby.Pupil.copy(alpha = a), 2f, Offset(x, 46f))
                }
            }
            if (state == ChumsState.APPROVAL || state == ChumsState.NEEDS_YOU) {
                drawPath(p(HOUSE), Tabby.PorchBlue, style = Stroke(3f, join = StrokeJoin.Round))
                drawPath(p(HOUSE_DOOR), Tabby.PorchBlue, style = Stroke(2.4f, join = StrokeJoin.Round))
            }
        }
    }
}

private fun DrawScope.drawCat(
    pose: Pose, alpha: Float, earsUp: Float, rightEarUp: Float, earsLow: Float, flick: Float, lids: Float,
    white: Float, pupil: Float, px: Float, py: Float, rpx: Float, eyes: Eyes, mouthOpen: Boolean, asleep: Boolean,
) {
    val fur = Tabby.Fur.copy(alpha = alpha)
    val stripe = Tabby.Stripe.copy(alpha = alpha)
    val cream = Tabby.Cream.copy(alpha = alpha)
    val dark = Tabby.Pupil.copy(alpha = alpha)
    val round = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // tail: tabby, its stripe bands, a cream tip
    val (tail, tip) = when (pose) {
        Pose.MESSAGE -> TAIL_HOOK to TAIL_HOOK_TIP
        Pose.ALERT -> TAIL_UP to TAIL_UP_TIP
        else -> TAIL to TAIL_TIP
    }
    drawPath(p(tail), fur, style = Stroke(TAIL_BANDS_W, cap = StrokeCap.Round))
    drawPath(p(tail), stripe, style = Stroke(TAIL_BANDS_W, pathEffect = tailBands))
    drawPath(p(tip), cream, style = Stroke(TAIL_BANDS_W, cap = StrokeCap.Round))

    drawPath(p(BODY), fur)
    drawPath(p(BODY_STRIPES), stripe, style = round)
    drawPath(p(BIB), cream)

    // ears: up for listening, a message and alerts; the right one alone while thinking; low while asleep
    val left = if (earsLow > 0f) lerpPath(EAR_L, EAR_L_LOW, earsLow) else lerpPath(EAR_L, EAR_L_UP, earsUp)
    val leftIn = if (earsLow > 0f) lerpPath(EAR_L_IN, EAR_L_LOW_IN, earsLow) else lerpPath(EAR_L_IN, EAR_L_UP_IN, earsUp)
    val rightT = maxOf(earsUp, rightEarUp)
    val right = if (earsLow > 0f) lerpPath(EAR_R, EAR_R_LOW, earsLow) else lerpPath(EAR_R, EAR_R_UP, rightT)
    val rightIn = if (earsLow > 0f) lerpPath(EAR_R_IN, EAR_R_LOW_IN, earsLow) else lerpPath(EAR_R_IN, EAR_R_UP_IN, rightT)
    rotate(flick, Offset(81f, 66f)) {                           // the flick turns on the ear's base
        drawPath(left, fur); drawPath(left, fur, style = Stroke(4f, join = StrokeJoin.Round)); drawPath(leftIn, stripe)
    }
    drawPath(right, fur); drawPath(right, fur, style = Stroke(4f, join = StrokeJoin.Round)); drawPath(rightIn, stripe)

    drawPath(p(HEAD), fur)
    drawPath(p(FACE_MARKS), stripe, style = round)

    // eyes: always round (no lids on this cat); a blink squashes them; happy arcs for a message
    when {
        asleep -> drawPath(p(SLEEP_EYES), dark, style = Stroke(4.5f, cap = StrokeCap.Round))
        pose == Pose.MESSAGE -> drawPath(p(HAPPY_EYES), dark, style = Stroke(5f, cap = StrokeCap.Round))
        else -> scale(1f, lids.coerceAtLeast(0.08f), Offset(100f, 80f)) {
            for ((cx, pxx, gx) in listOf(Triple(80f, px, eyes.gx), Triple(120f, rpx, eyes.gx + if (pose == Pose.ALERT) 36f else 40f))) {
                drawCircle(cream, white, Offset(cx, 80f))
                drawCircle(dark, pupil, Offset(pxx, py))
                drawCircle(cream, eyes.glint, Offset(gx, eyes.gy))
                if (eyes.small > 0f) drawCircle(cream, eyes.small, Offset(gx + eyes.sdx, eyes.gy + eyes.sdy))
            }
        }
    }

    drawPath(p(WHISKERS), cream, style = Stroke(2f, cap = StrokeCap.Round))
    drawPath(p(NOSE), Tabby.Nose.copy(alpha = alpha))
    drawPath(p(NOSE), Tabby.Nose.copy(alpha = alpha), style = Stroke(2.5f, join = StrokeJoin.Round))
    val mouth = Tabby.Mouth.copy(alpha = alpha)
    when {
        pose == Pose.MESSAGE && mouthOpen -> { drawPath(p(MOUTH_OPEN), mouth); drawPath(p(MOUTH_OPEN), mouth, style = Stroke(2f, join = StrokeJoin.Round)) }
        pose == Pose.THINKING -> drawPath(p(MOUTH_FLAT), mouth, style = Stroke(2.2f, cap = StrokeCap.Round))
        pose == Pose.ALERT -> drawCircle(mouth, 2.6f, Offset(100f, 106f))
        else -> drawPath(p(MOUTH_W), mouth, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/// Basic mode (the hub is out of reach): the cat as an outline at low brightness, ears out, a moon above.
private fun DrawScope.drawBasic(alpha: Float) {
    val fur = Tabby.Fur.copy(alpha = alpha)
    val cream = Tabby.Cream.copy(alpha = alpha)
    val line = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(p(B_TAIL), fur, style = line)
    drawPath(p(BODY), fur, style = line)
    drawPath(p(B_BIB), cream, style = Stroke(2.5f))
    drawPath(p(B_EARS), fur, style = line)
    drawPath(p(HEAD), Tabby.Void)
    drawPath(p(HEAD), fur, style = Stroke(3f))
    for (cx in listOf(80f, 120f)) {
        drawCircle(cream, 11f, Offset(cx, 80f), style = Stroke(2.5f))
        drawCircle(cream, 4f, Offset(cx, 81f))
    }
    drawPath(p(B_WHISKERS), cream, style = Stroke(1.6f, cap = StrokeCap.Round))
    drawPath(p(B_NOSE), Tabby.Nose.copy(alpha = alpha))
    drawPath(p(B_MOUTH), fur, style = Stroke(2f, cap = StrokeCap.Round))
    drawCircle(cream, 9f, Offset(100f, 22f))                    // the moon: a cream disc with a bite of night
    drawCircle(Tabby.Void, 7.5f, Offset(104.5f, 18f))
}

@Preview(widthDp = 960, heightDp = 160, backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun GarfieldStatesPreview() {
    androidx.compose.foundation.layout.Row {
        for (s in listOf(ChumsState.IDLE, ChumsState.LISTENING, ChumsState.THINKING, ChumsState.SPEAKING, ChumsState.APPROVAL, ChumsState.SLEEP)) {
            Box(Modifier.size(160.dp).background(Tabby.Void)) { GarfieldRig(s) }
        }
    }
}
