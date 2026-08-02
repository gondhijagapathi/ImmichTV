package com.jagapathi.immichtv.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    
    val qrCodeBitmap = remember(viewModel.serverUrlForQr) {
        QrCodeGenerator.generateQrCode(viewModel.serverUrlForQr, 400)
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
            Image(
                bitmap = qrCodeBitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.size(250.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Visit: ${viewModel.serverUrlForQr}",
                fontSize = 14.sp
            )
            Text(
                text = "on your phone (same Wi-Fi)",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
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

            OutlinedTextField(
                value = serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { androidx.compose.material3.Text("Server URL") },
                placeholder = { androidx.compose.material3.Text("https://your-immich-instance.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { androidx.compose.material3.Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
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
