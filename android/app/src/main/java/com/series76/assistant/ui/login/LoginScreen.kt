package com.series76.assistant.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.ui.components.BrandMark
import com.series76.assistant.ui.theme.Series76Theme

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
    val canSubmit = !uiState.loading && email.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onSubmit(email, password) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 88.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrandMark(size = 56.dp)
                Spacer(Modifier.height(4.dp))
                Text("Series 76", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Find the right electric actuator by part number or by requirements, with every figure taken " +
                        "from the data tables.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !uiState.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !uiState.loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = submit,
                    enabled = canSubmit,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
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
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    Series76Theme { LoginScreen(LoginUiState(error = "Incorrect email or password"), onSubmit = { _, _ -> }) }
}
