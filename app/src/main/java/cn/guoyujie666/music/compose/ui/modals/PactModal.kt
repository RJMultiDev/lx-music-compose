package cn.guoyujie666.music.compose.ui.modals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * User agreement / pact modal.
 *
 * Ported from the PactModal overlay.
 * Shows the app's license terms and requires user acceptance.
 */
@Composable
fun PactModal(
    visible: Boolean,
    onAgree: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = {}, // Cannot dismiss by tapping outside
        title = {
            Text(
                text = "User Agreement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                pactContent.forEach { section ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("Agree & Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("Exit", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

private data class PactSection(val title: String, val body: String)

private val pactContent = listOf(
    PactSection(
        "1. Data Sources",
        "This app retrieves online data from publicly accessible servers of music platforms. " +
        "It is provided for learning and research purposes only. The app is not responsible for " +
        "the legality or accuracy of data retrieved."
    ),
    PactSection(
        "2. Copyright",
        "This project does not own any copyright for audio data, images, or names used. " +
        "Users must clear any copyright data generated during use within 24 hours."
    ),
    PactSection(
        "3. Non-Commercial",
        "This project is free and open-source. It does not accept commercial partnerships " +
        "or donations. It is intended solely for technical exploration and research."
    ),
    PactSection(
        "4. Usage Restrictions",
        "Using this software in violation of local laws and regulations is strictly prohibited. " +
        "Users bear full responsibility for any violations."
    ),
    PactSection(
        "5. Music Platform Aliases",
        "Platform aliases used in this app are for reference only and do not imply any " +
        "affiliation with or endorsement by those platforms."
    ),
    PactSection(
        "6. Disclaimer",
        "By using this software, you accept this agreement. The developer is not liable for " +
        "any direct, indirect, or consequential damages arising from use of this software."
    )
)
