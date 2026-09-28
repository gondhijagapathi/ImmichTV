package com.jagapathi.immichtv.ui.people

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.components.itemCount
import com.jagapathi.immichtv.ui.timeline.PhotoTimeline
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A person's page: their face, name and details pinned at the top, and the photos they're in below. */
@Composable
fun PersonScreen(
    personId: String,
    onBack: () -> Unit
) {
    val viewModel = hiltViewModel<PersonViewModel, PersonViewModel.Factory> { factory -> factory.create(personId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            PersonUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is PersonUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorMessage(
                    message = current.message,
                    actionLabel = stringResource(if (current.isGone) R.string.back else R.string.retry),
                    onAction = if (current.isGone) onBack else viewModel::retry,
                    requestFocus = true
                )
            }
            is PersonUiState.Ready -> Column {
                PersonHeader(current.person, current.assetCount)
                PhotoTimeline(
                    query = current.person.timeline,
                    emptyText = stringResource(R.string.person_empty, current.person.name),
                    requestInitialFocus = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PersonHeader(person: PersonUi, assetCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 4.dp)
    ) {
        PersonAvatar(
            person = person,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = person.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(itemCount(assetCount), person.birthDate?.let { birthDateLabel(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** e.g. "Born on Apr 12, 1990", in the device's language. */
@Composable
private fun birthDateLabel(birthDate: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdyyyy"), locale)
    }
    return stringResource(R.string.person_born, format.format(birthDate))
}
