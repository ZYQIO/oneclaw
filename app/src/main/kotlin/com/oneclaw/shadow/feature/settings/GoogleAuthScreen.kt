package com.oneclaw.shadow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAuthScreen(
    viewModel: GoogleAuthViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            StatusCard(
                isSignedIn = uiState.isSignedIn,
                email = uiState.accountEmail
            )

            // Unverified app warning
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "About the \"unverified app\" warning",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "During sign-in, Google may show a warning saying \"Google hasn't verified this app.\" " +
                            "This is expected. Click \"Advanced\" then \"Go to ... (unsafe)\" to proceed. " +
                            "This is safe -- the app is fully on-device with no backend server, so your data never leaves your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // OAuth Credentials Section
            Text(
                text = "Custom OAuth",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            CredentialsSection(
                uiState = uiState,
                onClientIdChanged = viewModel::onClientIdChanged,
                onClientSecretChanged = viewModel::onClientSecretChanged,
                onSaveCredentials = viewModel::saveCredentials,
                onSignIn = viewModel::signIn,
                onSignOut = viewModel::signOut,
                onEditCredentials = viewModel::startEditingCredentials,
                onCancelEdit = viewModel::cancelEditingCredentials,
                onDeleteCredentials = viewModel::deleteCredentials
            )

            // Error display
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(isSignedIn: Boolean, email: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            if (isSignedIn) {
                Text(
                    text = if (email != null) "Connected: $email" else "Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Workspace plugins are available (via Custom OAuth)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Optional. Only needed for Google Workspace plugins (Gmail, Calendar, Drive, Tasks, Contacts, Docs, Sheets, Slides, Forms).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isSignedIn) {
        PermissionsCard()
    }
}

@Composable
private fun PermissionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Requested Permissions",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            val permissions = listOf(
                "Gmail -- read, send, and manage messages, labels, drafts, filters, and settings",
                "Calendar -- view and manage events, free/busy, and calendars",
                "Tasks -- view and manage task lists and tasks",
                "Contacts -- view and manage contacts",
                "Drive -- view and manage files and folders",
                "Docs -- view and edit documents",
                "Sheets -- view and edit spreadsheets",
                "Slides -- view and edit presentations",
                "Forms -- view form structure and responses (read-only)"
            )
            permissions.forEach { perm ->
                Text(
                    "- $perm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CredentialsSection(
    uiState: GoogleAuthViewModel.UiState,
    onClientIdChanged: (String) -> Unit,
    onClientSecretChanged: (String) -> Unit,
    onSaveCredentials: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onEditCredentials: () -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteCredentials: () -> Unit
) {
    var secretVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Bring your own GCP OAuth client credentials. No quota limits and full control over your tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (uiState.isSignedIn && uiState.hasCredentials && !uiState.editingCredentials) {
                // Signed in state
                Text(
                    text = if (uiState.accountEmail != null) "Connected: ${uiState.accountEmail}" else "Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect Custom OAuth")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onEditCredentials,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change OAuth Credentials")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDeleteCredentials,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Credentials")
                }
            } else if (uiState.hasCredentials && !uiState.isSignedIn && !uiState.editingCredentials) {
                // Has credentials, not signed in
                Text(
                    text = "OAuth credentials configured. Authorize to connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting for authorization...")
                    } else {
                        Text("Authorize with Google")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "If authorization fails, return to this screen and try again. The first attempt may fail due to a network timing issue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEditCredentials,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change OAuth Credentials")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDeleteCredentials,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Credentials")
                }
            } else {
                // Show credential input (new setup or editing)
                if (!uiState.editingCredentials) {
                    SetupInstructions()
                }

                OutlinedTextField(
                    value = uiState.clientId,
                    onValueChange = onClientIdChanged,
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.clientSecret,
                    onValueChange = onClientSecretChanged,
                    label = { Text("Client Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                imageVector = if (secretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (secretVisible) "Hide secret" else "Show secret"
                            )
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onSaveCredentials,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.dirty && uiState.clientId.isNotBlank() && uiState.clientSecret.isNotBlank()
                ) {
                    if (!uiState.dirty && uiState.hasCredentials) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Saved")
                    } else {
                        Text("Save Credentials")
                    }
                }

                if (uiState.editingCredentials) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupInstructions() {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val bodySmall = MaterialTheme.typography.bodySmall
    val context = LocalContext.current

    val apiList = listOf(
        "Gmail API", "Google Calendar API", "Google Tasks API",
        "People API", "Google Drive API", "Google Docs API",
        "Google Sheets API", "Google Slides API", "Google Forms API"
    )
    val scopeList = listOf(
        "gmail.modify", "gmail.settings.basic",
        "calendar", "tasks", "contacts",
        "drive", "documents", "spreadsheets",
        "presentations", "forms.body.readonly",
        "forms.responses.readonly"
    )

    @Composable
    fun numberedStep(step: Int, text: AnnotatedString) {
        val label = "$step. "
        Row(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = bodySmall, color = textColor)
            Text(text = text, style = bodySmall, modifier = Modifier.weight(1f))
        }
    }

    @Composable
    fun numberedStep(step: Int, text: String) {
        numberedStep(step, AnnotatedString(text))
    }

    @Composable
    fun nestedItem(text: String) {
        Row(modifier = Modifier.padding(start = 28.dp)) {
            Text(text = "\u2022 ", style = bodySmall, color = textColor)
            Text(text = text, style = bodySmall, color = textColor, modifier = Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(4.dp))

    val consoleUrl = "https://console.cloud.google.com"
    val step1Text = buildAnnotatedString {
        withStyle(SpanStyle(color = textColor)) {
            append("Go to ")
        }
        pushStringAnnotation(tag = "URL", annotation = consoleUrl)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("console.cloud.google.com")
        }
        pop()
        withStyle(SpanStyle(color = textColor)) {
            append(" and create a project")
        }
    }
    androidx.compose.foundation.text.ClickableText(
        text = step1Text,
        style = bodySmall,
        modifier = Modifier.padding(start = 4.dp),
        onClick = { offset ->
            step1Text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(annotation.item))
                    )
                }
        }
    )

    numberedStep(2, "Enable APIs in APIs & Services > Library:")
    apiList.forEach { api -> nestedItem(api) }

    numberedStep(3, "Go to APIs & Services > OAuth consent screen")
    numberedStep(4, "Under Branding: set an app name, user support email, and developer email")
    numberedStep(5, "Under Audience: select External, then click Publish App (this keeps your refresh tokens valid indefinitely)")

    numberedStep(6, "Under Data Access: click Add or Remove Scopes, add:")
    nestedItem("(filter by the APIs you enabled above to find them)")
    scopeList.forEach { scope -> nestedItem(scope) }

    numberedStep(7, "Go to APIs & Services > Credentials > + Create Credentials > OAuth client ID")
    numberedStep(8, "Set Application type to \"Desktop app\", give it any name, click Create")
    numberedStep(9, "Copy the Client ID and Client Secret below")

    Spacer(Modifier.height(8.dp))
}
