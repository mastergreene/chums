package club.chums.android.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.chums.android.data.Approval
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ApprovalCard(
    approval: Approval,
    onVerdict: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(targetValue = holdProgress, label = "HoldProgress")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF15101F))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = approval.tool.uppercase(),
                    color = Color(0xFFF2AE08),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onVerdict(false)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Deny",
                        tint = Color(0x99ECECEC)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = approval.description.ifEmpty { approval.preview },
                color = Color(0xFFECECEC),
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Hold-to-Allow button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF221E2C))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                val startTime = System.currentTimeMillis()
                                val holdDuration = 1000L

                                val job = scope.launch {
                                    while (isHolding) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val p = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                                        holdProgress = p
                                        if (p >= 1f) {
                                            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                                            onVerdict(true)
                                            break
                                        }
                                        delay(16)
                                    }
                                }
                                tryAwaitRelease()
                                isHolding = false
                                holdProgress = 0f
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(48.dp)
                        .background(Color(0xFFF2AE08))
                        .align(Alignment.CenterStart)
                )

                Text(
                    text = if (isHolding) "Keep holding..." else "Hold to Allow",
                    color = if (holdProgress > 0.5f) Color(0xFF040201) else Color(0xFFECECEC),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}
