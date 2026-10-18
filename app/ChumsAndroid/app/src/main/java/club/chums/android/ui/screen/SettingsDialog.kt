package club.chums.android.ui.screen

import androidx.compose.foundation.background
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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15101F), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Hub Settings",
                color = Color(0xFFECECEC),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Hub URL (its watch API)") },
                placeholder = { Text("http://hub-address:7718") },
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
                label = { Text("Watch token") },
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

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color(0x99ECECEC))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(url, token, volumeKeyTalks) },
                    enabled = url.isNotBlank() && token.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2AE08)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", color = Color(0xFF040201))
                }
            }
        }
    }
}
