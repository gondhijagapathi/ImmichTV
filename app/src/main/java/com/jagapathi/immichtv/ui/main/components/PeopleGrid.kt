package com.jagapathi.immichtv.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jagapathi.immichtv.model.ImmichPersonResponseDto
import com.jagapathi.immichtv.network.LocalImmichApiService

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PeopleGrid(
    people: List<ImmichPersonResponseDto>,
    modifier: Modifier = Modifier
) {
    val apiService = LocalImmichApiService.current
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = people,
            key = { it.id }
        ) { person ->
            PeopleItem(
                person = person,
                thumbnailUrl = apiService.getPersonThumbnailUrl(person.id),
                apiKey = apiService.apiKey ?: "",
                onClick = { /* Handle click if needed */ }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PeopleItem(
    person: ImmichPersonResponseDto,
    thumbnailUrl: String,
    apiKey: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            val imageRequest = remember(thumbnailUrl, apiKey) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .addHeader("x-api-key", apiKey)
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = person.name,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                error = rememberVectorPainter(Icons.Default.AccountCircle)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
