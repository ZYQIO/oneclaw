package com.oneclaw.shadow.feature.schedule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExactAlarmPermissionDialog(
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exact Alarm Permission Required") },
        text = {
            Text(
                "To run scheduled tasks at the exact times you set, " +
                    "this app needs the \"Alarms & reminders\" permission. " +
                    "Without it, your scheduled tasks may not trigger on time.\n\n" +
                    "Tap \"Go to Settings\" to grant the permission."
            )
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
