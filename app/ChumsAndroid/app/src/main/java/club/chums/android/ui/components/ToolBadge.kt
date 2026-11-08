package club.chums.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.chums.android.data.ToolInfo

@Composable
fun ToolBadge(
    currentTool: ToolInfo?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentTool != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 }),
        modifier = modifier
    ) {
        if (currentTool != null) {
            val label = when (currentTool.type.lowercase()) {
                "read" -> "+1 READ"
                "edit" -> "+1 EDIT"
                "bash" -> "+1 BASH"
                "search", "grep" -> "+1 GREP"
                "glob" -> "+1 GLOB"
                else -> "+1 ${currentTool.type.uppercase()}"
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF221E2C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = Color(0xFFF9ECD5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
