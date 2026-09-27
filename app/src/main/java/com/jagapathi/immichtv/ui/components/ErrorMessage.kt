package com.jagapathi.immichtv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Explains what went wrong, with a button below to do something about it, such as retrying.
 * [requestFocus] focuses the button, for screens where nothing else can take focus.
 */
@Composable
fun ErrorMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAction, modifier = Modifier.focusRequester(focusRequester)) {
            Text(actionLabel)
        }
    }
}
