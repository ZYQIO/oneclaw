package com.oneclaw.shadow.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskListScreen(
    onNavigateBack: () -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (String) -> Unit,
    viewModel: ScheduledTaskListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteConfirmTaskId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Tasks") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTask) {
                Icon(Icons.Default.Add, contentDescription = "Create Task")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No scheduled tasks yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(uiState.tasks, key = { it.id }) { task ->
                    ScheduledTaskItem(
                        task = task,
                        onToggle = { enabled -> viewModel.toggleTask(task.id, enabled) },
                        onEdit = { onEditTask(task.id) },
                        onDelete = { deleteConfirmTaskId = task.id }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    deleteConfirmTaskId?.let { taskId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTaskId = null },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete this scheduled task?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(taskId)
                    deleteConfirmTaskId = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTaskId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScheduledTaskItem(
    task: ScheduledTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatScheduleDescription(task),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            task.lastExecutionStatus?.let { status ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (status) {
                        ExecutionStatus.RUNNING -> "Running..."
                        ExecutionStatus.SUCCESS -> "Last run: succeeded"
                        ExecutionStatus.FAILED -> "Last run: failed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        ExecutionStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        ExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                        ExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = task.isEnabled,
            onCheckedChange = onToggle
        )

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

internal fun formatScheduleDescription(task: ScheduledTask): String {
    val timeStr = "%02d:%02d".format(task.hour, task.minute)
    return when (task.scheduleType) {
        ScheduleType.ONE_TIME -> "One-time at $timeStr"
        ScheduleType.DAILY -> "Daily at $timeStr"
        ScheduleType.WEEKLY -> {
            val dayName = task.dayOfWeek?.let {
                DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault())
            } ?: "Monday"
            "Every $dayName at $timeStr"
        }
    }
}
