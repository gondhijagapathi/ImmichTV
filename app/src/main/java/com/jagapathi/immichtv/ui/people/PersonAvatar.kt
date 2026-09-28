package com.jagapathi.immichtv.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.jagapathi.immichtv.R

/**
 * The person's face, filling a container its parent clips to a circle. A generic avatar shows
 * while it loads and stays if it can't be.
 */
@Composable
internal fun PersonAvatar(person: PersonUi, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_account_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.5f)
        )
        AsyncImage(
            model = person.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
