package club.chums.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import club.chums.android.data.validHubUrl
import club.chums.android.HubConnectionService
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SettingsDialog(
    initialUrl: String,
    initialToken: String,
    initialVolumeKeyTalks: Boolean,
    onSave: (url: String, token: String, volumeKeyTalks: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var volumeKeyTalks by remember { mutableStateOf(initialVolumeKeyTalks) }
    val context = LocalContext.current
    var background by remember { mutableStateOf(context.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE).getBoolean("background_connection", false)) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) { background = true; HubConnectionService.setEnabled(context, true) }
        else notificationError = "Allow notifications to receive agent requests in the background."
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15101F), RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Hub Settings",
                color = Color(0xFFECECEC),
                fontSize = 18.sp
            )
            Text("Connect to the Chums hub on your Mac. Use its HTTPS address and device token.", color = Color(0xFFB0B0B0), fontSize = 13.sp)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Mac hub address") },
                placeholder = { Text("https://your-mac.your-tailnet.ts.net") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFECECEC),
                    unfocusedTextColor = Color(0xFFECECEC),
                    focusedBorderColor = Color(0xFFF2AE08),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedLabelColor = Color(0xFFF2AE08),
                    unfocusedLabelColor = Color(0x99ECECEC)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Device token (watchToken)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFECECEC),
                    unfocusedTextColor = Color(0xFFECECEC),
                    focusedBorderColor = Color(0xFFF2AE08),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedLabelColor = Color(0xFFF2AE08),
                    unfocusedLabelColor = Color(0x99ECECEC)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hold volume up to talk", color = Color(0xFFECECEC), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = volumeKeyTalks, onCheckedChange = { volumeKeyTalks = it })
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep connected in background", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = background, enabled = initialUrl.isNotBlank(), onCheckedChange = { enabled ->
                    notificationError = null
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        background = enabled
                        HubConnectionService.setEnabled(context, enabled)
                    }
                })
            }
            Text("Shows a persistent notification and uses battery. Tap agent alerts to review them in Chums.", color = Color(0xFFB0B0B0), fontSize = 12.sp)
            notificationError?.let { Text(it, color = Color(0xFFFFB74D)) }

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color(0x99ECECEC))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(url, token, volumeKeyTalks)
                        if (background) HubConnectionService.setEnabled(context, true)
                    },
                    enabled = validHubUrl(url) && token.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2AE08)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", color = Color(0xFF040201))
                }
            }
        }
    }
}
