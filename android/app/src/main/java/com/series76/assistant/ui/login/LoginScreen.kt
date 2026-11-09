package com.series76.assistant.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.R
import com.series76.assistant.ui.components.BrandMark
import com.series76.assistant.ui.theme.Series76Theme
import com.series76.assistant.ui.components.ErrorMessage

@Composable
fun LoginRoute(viewModel: LoginViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(uiState, onSubmit = viewModel::login)
}

@Composable
fun LoginScreen(uiState: LoginUiState, onSubmit: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // The password is deliberately not saveable, so it never lands in the saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val canSubmit = !uiState.loading && email.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onSubmit(email, password) }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().draftingGrid().imePadding()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // At least a screen tall, so the form sits in the middle and still scrolls once the keyboard is up.
                Column(
                    modifier =
                        Modifier.heightIn(min = this@BoxWithConstraints.maxHeight)
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Masthead()
                    Spacer(Modifier.height(16.dp))
                    Text("Sign in", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        enabled = !uiState.loading,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !uiState.loading,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.error?.let {
                        ErrorMessage(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = submit,
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (uiState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Sign in")
                        }
                    }
                    SourceNote()
                }
            }
        }
    }
}

/**
 * A drafting grid across the top of the screen that fades into the page, so the sign-in reads like the top of a data
 * sheet, the register of the documents its users already read. It runs under the status bar, behind the content.
 */
@Composable
private fun Modifier.draftingGrid(): Modifier {
    // Faint, so the grid reads as paper and never competes with the words over it.
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val surface = MaterialTheme.colorScheme.surface
    return drawBehind {
        val band = size.height * 0.4f
        drawRect(paper, size = Size(size.width, band))
        val step = 16.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, band), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < band) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        drawRect(Brush.verticalGradient(0.2f to Color.Transparent, 1f to surface, endY = band), size = Size(size.width, band))
    }
}

/** What the tool is before anyone signs in, kept to a few lines so the form stays the center of the screen. */
@Composable
private fun Masthead() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandMark(size = 48.dp)
            Column {
                Text(
                    "ELECTRIC ACTUATORS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Series 76", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(
            "Look up a part number or find the model that meets your needs.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Every figure comes from the manufacturer's data tables.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    Series76Theme { LoginScreen(LoginUiState(error = "Incorrect email or password"), onSubmit = { _, _ -> }) }
}
