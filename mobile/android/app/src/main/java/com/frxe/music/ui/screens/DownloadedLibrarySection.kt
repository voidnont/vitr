package com.frxe.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.model.Track
import com.frxe.music.save.DownloadQueueActions
import com.frxe.music.save.DownloadQueueStore
import com.frxe.music.save.DownloadedLibraryPolicy
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.playQueued
import com.frxe.music.ui.components.GeneratedArtwork
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.components.LiquidIconButton
import com.frxe.music.ui.components.springPress

@Composable
internal fun DownloadedLibrarySection(
    viewModel: FrxeViewModel,
    onOpenPlayer: (Track) -> Unit
) {
    val context = LocalContext.current
    val queueItems by DownloadQueueStore.items.collectAsState()
    val entries = remember(queueItems) {
        DownloadedLibraryPolicy.entries(queueItems)
    }
    val playbackQueue = remember(entries) {
        entries.map { it.track }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Downloads",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (entries.isEmpty()) {
            GlassPanel(
                Modifier.fillMaxWidth(),
                radius = 24.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("No downloads yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Finished downloads will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            entries.forEach { entry ->
                val interactionSource = remember(entry.queueItemId) {
                    MutableInteractionSource()
                }
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPress(interactionSource)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onOpenPlayer(entry.track)
                            viewModel.playQueued(entry.track, playbackQueue)
                        },
                    radius = 20.dp,
                    padding = PaddingValues(10.dp),
                    strong = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GeneratedArtwork(entry.track, size = 58.dp, radius = 16.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(entry.track.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                entry.track.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                listOf(entry.format, entry.quality)
                                    .filter(String::isNotBlank)
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LiquidIconButton(
                            onClick = {
                                DownloadQueueActions.redownload(
                                    context,
                                    entry.queueItemId
                                )
                            },
                            size = 38.dp
                        ) {
                            Icon(Icons.Default.Refresh, "Redownload")
                        }
                        LiquidIconButton(
                            onClick = {
                                DownloadQueueActions.removeDownloadedFile(
                                    context,
                                    entry.queueItemId
                                )
                            },
                            size = 38.dp
                        ) {
                            Icon(Icons.Default.Delete, "Delete download")
                        }
                    }
                }
            }
        }
    }
}
