package com.fitforge.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.BuildConfig
import com.fitforge.app.R

// Matches the fixed account `manage.py seed_demo_account` creates on the
// backend. Not a secret — it's the credential behind this very button.
private const val DEMO_EMAIL = "demo@fitforge.app"
private const val DEMO_PASSWORD = "FitForgeDemo!2026"

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGoToSignup: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (uiState is LoginUiState.Error) {
            Text(
                text = stringResource(R.string.login_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { viewModel.login(email, password, onLoginSuccess) },
            enabled = email.isNotBlank() && password.isNotBlank() && uiState !is LoginUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.login_button))
            }
        }

        TextButton(onClick = onGoToSignup, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.login_go_to_signup))
        }

        // BuildConfig.DEBUG is compiled per variant by AGP itself — false in
        // every release build — so this can't ship by accident the way a
        // stray runtime flag could.
        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = { viewModel.login(DEMO_EMAIL, DEMO_PASSWORD, onLoginSuccess) },
                enabled = uiState !is LoginUiState.Loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.login_try_demo))
            }
        }
    }
}
