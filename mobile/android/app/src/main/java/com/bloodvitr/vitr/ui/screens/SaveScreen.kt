package com.bloodvitr.vitr.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloodvitr.vitr.intake.UrlIntakeParser
import com.bloodvitr.vitr.model.Track
import com.bloodvitr.vitr.save.DownloadQueueActions
import com.bloodvitr.vitr.save.DownloadQueueItem
import com.bloodvitr.vitr.save.DownloadQueueItemState
import com.bloodvitr.vitr.save.DownloadQueueStore
import com.bloodvitr.vitr.save.DownloadUiPrivacyPolicy
import com.bloodvitr.vitr.save.VitrDownloadService
import com.bloodvitr.vitr.save.InspectedDownloadMatchingPolicy
import com.bloodvitr.vitr.save.SaveFormat
import com.bloodvitr.vitr.save.SaveQuality
import com.bloodvitr.vitr.save.SaveRequest
import com.bloodvitr.vitr.save.defaultQualityFor
import com.bloodvitr.vitr.save.qualityOptionsFor
import com.bloodvitr.vitr.ui.VitrViewModel
import com.bloodvitr.vitr.ui.consumeExternalUrl
import com.bloodvitr.vitr.ui.inspectUrl
import com.bloodvitr.vitr.ui.inspectionState
import com.bloodvitr.vitr.ui.pendingExternalUrl
import com.bloodvitr.vitr.ui.components.GeneratedArtwork
import com.bloodvitr.vitr.ui.components.GlassPanel
import com.bloodvitr.vitr.ytdlp.YtDlpDownloadRequest
import com.bloodvitr.vitr.ytdlp.YtDlpInspectionUiState
import com.bloodvitr.vitr.ytdlp.YtDlpMediaFormat
import com.bloodvitr.vitr.ytdlp.YtDlpMediaKind
import com.bloodvitr.vitr.ytdlp.YtDlpPlaylistExpansionPolicy

@Composable
fun SaveScreen(
    viewModel: VitrViewModel,
    isTv: Boolean
) {
    // Keep the existing screen signature stable; queue state now lives outside the ViewModel.
    viewModel.playerState

    val context = LocalContext.current
    val queue by DownloadQueueStore.items.collectAsState()
    val wifiOnly by DownloadQueueStore.wifiOnly.collectAsState()
    val pendingExternalUrl by viewModel.pendingExternalUrl.collectAsState()
    val inspectionState by viewModel.inspectionState.collectAsState()

    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(SaveFormat.MP3) }
    var quality by remember { mutableStateOf(SaveQuality.Mp3K320) }
    var enqueueMessage by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedSubtitleLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var includeAutomaticCaptions by remember { mutableStateOf(false) }

    val previewTrack = remember(title, artist, url) {
        Track(
            id = "save-preview",
            title = title.ifBlank { "Vitr Save" },
            artist = artist.ifBlank { "Unknown artist" },
            album = "Vitr Save",
            streamUrl = url,
            durationMs = 0L,
            artworkSeed = (title + artist + url).hashCode()
        )
    }

    LaunchedEffect(format) {
        if (quality !in qualityOptionsFor(format)) {
            quality = defaultQualityFor(format)
        }
    }

    LaunchedEffect(pendingExternalUrl) {
        pendingExternalUrl?.let { sharedUrl ->
            url = sharedUrl
            viewModel.consumeExternalUrl()
            viewModel.inspectUrl(sharedUrl)
        }
    }

    LaunchedEffect(inspectionState) {
        val success =
            inspectionState as? YtDlpInspectionUiState.Success
                ?: return@LaunchedEffect
        val inspection = success.inspection

        if (title.isBlank()) {
            title = inspection.title
        }

        if (artist.isBlank()) {
            artist = inspection.creator.orEmpty()
        }

        selectedPlaylistIndices =
            if (inspection.isPlaylist) {
                inspection.entries
                    .mapIndexedNotNull { zeroIndex, entry ->
                        if (UrlIntakeParser.extractFirstHttpUrl(entry.url) != null) {
                            zeroIndex + 1
                        } else {
                            null
                        }
                    }
                    .toSet()
            } else {
                emptySet()
            }
        selectedSubtitleLanguages = emptySet()
        includeAutomaticCaptions = false
    }

    val active = queue.filter { it.state == DownloadQueueItemState.Running }
    val queued = queue.filter { it.state == DownloadQueueItemState.Queued }
    val paused = queue.filter { it.state == DownloadQueueItemState.Paused }
    val failed = queue.filter { it.state == DownloadQueueItemState.Failed }
    val cancelled = queue.filter { it.state == DownloadQueueItemState.Cancelled }
    val completed = queue.filter { it.state == DownloadQueueItemState.Complete }
        .sortedByDescending { it.updatedAtMs }
        .take(30)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isTv) 56.dp else 20.dp,
                end = if (isTv) 56.dp else 20.dp,
                top = 54.dp,
                bottom = 190.dp
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Downloads",
            fontSize = if (isTv) 42.sp else 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Queue multiple tracks. Vitr processes one conversion at a time so playback and the rest of the app stay responsive.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            radius = 26.dp,
            strong = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GeneratedArtwork(previewTrack, size = 64.dp, radius = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(previewTrack.title, fontWeight = FontWeight.Bold)
                        Text(
                            previewTrack.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                GlassTextField(url, { url = it }, "Media URL")

                QueueActionChip(
                    label = "Inspect link",
                    icon = Icons.Default.Search,
                    enabled =
                        url.isNotBlank() &&
                        inspectionState !is YtDlpInspectionUiState.Loading
                ) {
                    viewModel.inspectUrl(url)
                }

                when (val state = inspectionState) {
                    YtDlpInspectionUiState.Idle -> Unit

                    is YtDlpInspectionUiState.Loading ->
                        Text(
                            "Inspecting link…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                    is YtDlpInspectionUiState.Failure ->
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error
                        )

                    is YtDlpInspectionUiState.Success -> {
                        val inspection = state.inspection
                        val subtitleLanguages =
                            (
                                inspection.subtitleLanguages +
                                    inspection.automaticCaptionLanguages
                                )
                                .distinct()
                        val subtitleCount = subtitleLanguages.size

                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                inspection.title.ifBlank { "Media found" },
                                fontWeight = FontWeight.Bold
                            )

                            inspection.creator?.let { creator ->
                                Text(
                                    creator,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                buildString {
                                    append(
                                        if (inspection.isPlaylist) {
                                            "Playlist"
                                        } else {
                                            "Single item"
                                        }
                                    )
                                    inspection.durationMs?.let { durationMs ->
                                        append(" · ")
                                        append(durationLabel(durationMs))
                                    }
                                    append(" · ")
                                    append(inspection.formats.size)
                                    append(" formats")
                                    append(" · ")
                                    append(subtitleCount)
                                    append(" subtitle languages")
                                    if (inspection.isPlaylist) {
                                        append(" · ")
                                        append(inspection.entries.size)
                                        append(" items")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            inspection.formats
                                .take(8)
                                .forEach { mediaFormat ->
                                    Text(
                                        formatSummary(mediaFormat),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                            if (inspection.isPlaylist) {
                                Text(
                                    "Playlist items",
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VitrChoiceChip(
                                        label = "Select all",
                                        selected = false
                                    ) {
                                        selectedPlaylistIndices =
                                            inspection.entries
                                                .mapIndexedNotNull { zeroIndex, entry ->
                                                    if (
                                                        UrlIntakeParser.extractFirstHttpUrl(
                                                            entry.url
                                                        ) != null
                                                    ) {
                                                        zeroIndex + 1
                                                    } else {
                                                        null
                                                    }
                                                }
                                                .toSet()
                                    }
                                    VitrChoiceChip(
                                        label = "Deselect all",
                                        selected = false
                                    ) {
                                        selectedPlaylistIndices = emptySet()
                                    }
                                }

                                inspection.entries.forEachIndexed { zeroIndex, entry ->
                                    val sourceIndex = zeroIndex + 1
                                    val selectable =
                                        UrlIntakeParser.extractFirstHttpUrl(entry.url) != null
                                    val label =
                                        "$sourceIndex. ${entry.title.ifBlank { "Item $sourceIndex" }}"

                                    if (selectable) {
                                        VitrChoiceChip(
                                            label = label,
                                            selected = sourceIndex in selectedPlaylistIndices
                                        ) {
                                            selectedPlaylistIndices =
                                                if (sourceIndex in selectedPlaylistIndices) {
                                                    selectedPlaylistIndices - sourceIndex
                                                } else {
                                                    selectedPlaylistIndices + sourceIndex
                                                }
                                        }
                                    } else {
                                        Text(
                                            "$label · unavailable",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (subtitleLanguages.isNotEmpty()) {
                                Text(
                                    "Subtitle languages",
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subtitleLanguages.forEach { language ->
                                        VitrChoiceChip(
                                            label = language,
                                            selected = language in selectedSubtitleLanguages
                                        ) {
                                            selectedSubtitleLanguages =
                                                if (language in selectedSubtitleLanguages) {
                                                    selectedSubtitleLanguages - language
                                                } else {
                                                    selectedSubtitleLanguages + language
                                                }
                                        }
                                    }
                                }
                            }

                            if (inspection.automaticCaptionLanguages.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Include automatic captions",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (selectedSubtitleLanguages.isEmpty()) {
                                                "Choose at least one subtitle language first"
                                            } else {
                                                "Save automatic captions when available"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = includeAutomaticCaptions,
                                        enabled = selectedSubtitleLanguages.isNotEmpty(),
                                        onCheckedChange = {
                                            includeAutomaticCaptions = it
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(title, { title = it }, "Title", Modifier.weight(1f))
                    GlassTextField(artist, { artist = it }, "Artist", Modifier.weight(1f))
                }

                Text("Format", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SaveFormat.entries.forEach { item ->
                        VitrChoiceChip(
                            item.displayName,
                            selected = format == item
                        ) { format = item }
                    }
                }

                Text("Quality", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityOptionsFor(format).forEach { item ->
                        VitrChoiceChip(
                            item.label,
                            selected = quality == item
                        ) { quality = item }
                    }
                }

                QueueActionChip(
                    label = "Add to download queue",
                    icon = Icons.Default.Download,
                    enabled = url.isNotBlank()
                ) {
                    val successfulInspection =
                        inspectionState as? YtDlpInspectionUiState.Success
                    val useNativeRoute =
                        successfulInspection != null &&
                            InspectedDownloadMatchingPolicy.matches(
                                currentUrl = url,
                                inspectedUrl = successfulInspection.url
                            )

                    if (useNativeRoute) {
                        val inspection = successfulInspection.inspection
                        val baseRequest =
                            YtDlpDownloadRequest(
                                sourceUrl = url.trim(),
                                title = title.ifBlank { "Vitr export" },
                                artist = artist.ifBlank { "Unknown artist" },
                                mediaKind = YtDlpMediaKind.Audio,
                                outputFormat = format,
                                quality = quality,
                                playlistTitle = inspection.playlistTitle,
                                playlistIndex = inspection.playlistIndex,
                                subtitleLanguages = selectedSubtitleLanguages.sorted(),
                                writeAutoSubtitles = includeAutomaticCaptions,
                                embedSubtitles = false,
                                embedMetadata = true,
                                embedThumbnail = true,
                                thumbnailUrl = inspection.thumbnailUrl,
                                album = inspection.album,
                                trackNumber = inspection.trackNumber,
                                discNumber = inspection.discNumber,
                                releaseYear = inspection.releaseYear,
                                useAcceleratedDownloader = true
                            )

                        val requests =
                            if (inspection.isPlaylist) {
                                YtDlpPlaylistExpansionPolicy.expand(
                                    inspection = inspection,
                                    baseRequest = baseRequest,
                                    selectedPlaylistIndices = selectedPlaylistIndices
                                )
                            } else {
                                listOf(baseRequest)
                            }

                        if (requests.isEmpty()) {
                            enqueueMessage = "Select at least one downloadable item"
                            return@QueueActionChip
                        }

                        val results =
                            requests.map { request ->
                                VitrDownloadService.enqueue(
                                    context,
                                    request
                                )
                            }

                        if (inspection.isPlaylist) {
                            val addedCount = results.count { !it.duplicate }
                            val duplicateCount = results.size - addedCount
                            enqueueMessage =
                                when {
                                    addedCount > 0 && duplicateCount > 0 ->
                                        "Added $addedCount items · $duplicateCount already queued"

                                    addedCount > 0 ->
                                        "Added $addedCount items to download queue"

                                    duplicateCount > 0 ->
                                        "All selected items are already in download queue"

                                    else ->
                                        "Nothing added"
                                }
                        } else {
                            val result = results.single()
                            enqueueMessage =
                                if (result.duplicate) {
                                    when (result.item.state) {
                                        DownloadQueueItemState.Complete -> "Already downloaded"
                                        else -> "Already in download queue"
                                    }
                                } else {
                                    "Added to queue"
                                }
                        }
                    } else {
                        val result =
                            VitrDownloadService.enqueue(
                                context,
                                SaveRequest(
                                    sourceUrl = url.trim(),
                                    title = title.ifBlank { "Vitr export" },
                                    artist = artist.ifBlank { "Unknown artist" },
                                    format = format,
                                    quality = quality
                                )
                            )

                        enqueueMessage =
                            if (result.duplicate) {
                                when (result.item.state) {
                                    DownloadQueueItemState.Complete -> "Already downloaded"
                                    else -> "Already in download queue"
                                }
                            } else {
                                "Added to queue"
                            }
                    }
                }

                enqueueMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            radius = 24.dp,
            strong = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text("Wi-Fi only", fontWeight = FontWeight.Bold)
                        Text(
                            if (wifiOnly) "Queued downloads wait for Wi-Fi" else "Downloads can use any active network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            VitrDownloadService.setWifiOnly(context, it)
                        }
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueActionChip(
                        label = "Pause all",
                        icon = Icons.Default.Pause,
                        enabled = active.isNotEmpty() || queued.isNotEmpty()
                    ) {
                        VitrDownloadService.pauseAll(context)
                    }
                    QueueActionChip(
                        label = "Resume all",
                        icon = Icons.Default.PlayArrow,
                        enabled = paused.isNotEmpty()
                    ) {
                        VitrDownloadService.resumeAll(context)
                    }
                    QueueActionChip(
                        label = "Retry failed",
                        icon = Icons.Default.Refresh,
                        enabled = failed.isNotEmpty()
                    ) {
                        VitrDownloadService.retryAllFailed(context)
                    }
                    QueueActionChip(
                        label = "Clear failed/cancelled",
                        icon = Icons.Default.Delete,
                        enabled = failed.isNotEmpty() || cancelled.isNotEmpty()
                    ) {
                        VitrDownloadService.clearFailedAndCancelled(context)
                    }
                }
            }
        }

        QueueSection("Active", active, context)
        QueueSection("Queued", queued, context)
        QueueSection("Paused", paused, context)
        QueueSection("Needs attention", failed, context)
        QueueSection("Downloaded", completed, context)

        if (queue.none { it.state != DownloadQueueItemState.Cancelled }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 24.dp,
                strong = false
            ) {
                Text(
                    "Your download queue is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QueueSection(
    title: String,
    items: List<DownloadQueueItem>,
    context: android.content.Context
) {
    if (items.isEmpty()) return

    Text(
        "$title · ${items.size}",
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onBackground
    )

    items.forEach { item ->
        DownloadQueueCard(item, context)
    }
}

@Composable
private fun DownloadQueueCard(
    item: DownloadQueueItem,
    context: android.content.Context
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        strong = item.state == DownloadQueueItemState.Running
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { "Vitr export" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        item.artist.ifBlank { "Unknown artist" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    item.state.displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                buildString {
                    append(formatLabel(item.format))
                    append(" · ")
                    append(qualityLabel(item.quality))
                    if (item.retryCount > 0) {
                        append(" · retry ")
                        append(item.retryCount)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                DownloadUiPrivacyPolicy.sanitize(item.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.state == DownloadQueueItemState.Running) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item.state) {
                    DownloadQueueItemState.Running,
                    DownloadQueueItemState.Queued -> {
                        QueueActionChip("Pause", Icons.Default.Pause) {
                            VitrDownloadService.pause(context, item.id)
                        }
                        QueueActionChip("Cancel", Icons.Default.Cancel) {
                            VitrDownloadService.cancel(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Paused -> {
                        QueueActionChip("Resume", Icons.Default.PlayArrow) {
                            VitrDownloadService.resume(context, item.id)
                        }
                        QueueActionChip("Cancel", Icons.Default.Cancel) {
                            VitrDownloadService.cancel(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Failed -> {
                        QueueActionChip("Retry", Icons.Default.Refresh) {
                            VitrDownloadService.retry(context, item.id)
                        }
                        QueueActionChip("Remove", Icons.Default.Delete) {
                            VitrDownloadService.remove(item.id)
                        }
                    }

                    DownloadQueueItemState.Complete -> {
                        QueueActionChip("Redownload", Icons.Default.Refresh) {
                            DownloadQueueActions.redownload(context, item.id)
                        }
                        QueueActionChip("Remove file", Icons.Default.Delete) {
                            DownloadQueueActions.removeDownloadedFile(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Cancelled -> {
                        QueueActionChip("Remove", Icons.Default.Delete) {
                            VitrDownloadService.remove(item.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Black.copy(alpha = 0.16f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.10f),
            focusedBorderColor = Color.White.copy(alpha = 0.34f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
        )
    )
}

@Composable
private fun VitrChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.22f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun QueueActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(icon, contentDescription = null)
        },
        label = {
            Text(label, fontWeight = FontWeight.SemiBold)
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.22f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Black.copy(alpha = 0.10f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

private val DownloadQueueItemState.displayLabel: String
    get() = when (this) {
        DownloadQueueItemState.Queued -> "Queued"
        DownloadQueueItemState.Running -> "Downloading"
        DownloadQueueItemState.Paused -> "Paused"
        DownloadQueueItemState.Complete -> "Downloaded"
        DownloadQueueItemState.Failed -> "Failed"
        DownloadQueueItemState.Cancelled -> "Cancelled"
    }

private fun durationLabel(durationMs: Long): String {
    val totalSeconds =
        durationMs.coerceAtLeast(0L) / 1_000L
    val hours =
        totalSeconds / 3_600L
    val minutes =
        (totalSeconds % 3_600L) / 60L
    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatSummary(format: YtDlpMediaFormat): String =
    buildString {
        append(format.id.ifBlank { "format" })
        format.extension?.let {
            append(" · ")
            append(it.uppercase())
        }

        val hasAudio =
            !format.audioCodec.isNullOrBlank() &&
                format.audioCodec != "none"
        val hasVideo =
            !format.videoCodec.isNullOrBlank() &&
                format.videoCodec != "none"

        append(" · ")
        append(
            when {
                hasAudio && hasVideo -> "audio + video"
                hasVideo -> "video"
                hasAudio -> "audio"
                else -> "media"
            }
        )

        if (format.height != null) {
            append(" · ")
            append(format.height)
            append("p")
        } else if (format.audioBitrateKbps != null) {
            append(" · ")
            append(format.audioBitrateKbps.toInt())
            append(" kbps")
        }

        format.approximateSizeBytes?.let { bytes ->
            append(" · ~")
            append(bytes / (1_024L * 1_024L))
            append(" MB")
        }
    }

private fun formatLabel(value: String): String =
    runCatching { SaveFormat.valueOf(value).displayName }
        .getOrDefault(value)

private fun qualityLabel(value: String): String =
    runCatching { SaveQuality.valueOf(value).label }
        .getOrDefault(value)
