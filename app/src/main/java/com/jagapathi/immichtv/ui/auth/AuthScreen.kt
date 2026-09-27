package com.jagapathi.immichtv.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jagapathi.immichtv.util.QrCodeGenerator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loginSuccess by viewModel.loginSuccessEvent.collectAsState()
    val pairingUrl by viewModel.pairingUrl.collectAsState()
    val isPairingUnavailable by viewModel.isPairingUnavailable.collectAsState()

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            viewModel.resetLoginSuccessEvent()
            onLoginSuccess()
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // QR Code Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Scan to Login",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val url = pairingUrl
            if (url != null) {
                val qrCodeBitmap = remember(url) {
                    QrCodeGenerator.generateQrCode(url, 500).asImageBitmap()
                }
                Image(
                    bitmap = qrCodeBitmap,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(250.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Visit: $url",
                    fontSize = 14.sp
                )
                Text(
                    text = "on your phone (same Wi-Fi)",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPairingUnavailable) {
                            "Phone login isn't available right now. Use manual login instead."
                        } else {
                            "Connect the TV to Wi-Fi or Ethernet to log in with your phone."
                        },
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Manual Login Section
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Manual Login",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TvTextField(
                value = serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = "Server URL",
                placeholder = "https://your-immich-instance.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(16.dp))

            TvTextField(
                value = apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = "API Key",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.login()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Login")
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * A text field that can be focused with the D-pad without popping up the on-screen keyboard,
 * which would otherwise cover half the screen every time focus passes through. Pressing OK
 * starts editing; the keyboard's Next/Done action or moving focus away stops it.
 */
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isEditing) {
        if (isEditing) keyboardController?.show()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !isEditing,
        label = { androidx.compose.material3.Text(label) },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) isEditing = false }
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isEditing && isConfirm) {
                    if (event.type == KeyEventType.KeyUp) isEditing = true
                    true
                } else {
                    false
                }
            },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = {
            isEditing = false
            focusManager.moveFocus(FocusDirection.Down)
        })
    )
}
