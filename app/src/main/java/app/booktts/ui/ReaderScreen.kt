package app.booktts.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.booktts.R
import app.booktts.container
import app.booktts.data.BookContent
import app.booktts.export.ExportWorker
import app.booktts.playback.PlaybackController
import app.booktts.voice.VoiceRef
import app.booktts.voice.languageName

private val speeds = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit, onVoices: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val pc = c.playback
    LaunchedEffect(bookId) { pc.open(bookId) }

    val s by pc.state.collectAsStateWithLifecycle()
    val speed by c.settings.speedFlow.collectAsStateWithLifecycle()
    val installed by c.voices.installed.collectAsStateWithLifecycle()
    LaunchedEffect(installed) { pc.refreshVoice() }

    val book = s.book?.takeIf { it.id == bookId }
    val content = s.content.takeIf { book != null }

    var menu by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showVoices by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportChapters by remember { mutableStateOf<IntArray?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            ExportWorker.enqueue(context, bookId, uri, exportChapters)
        }
    }
    val exportInfos by remember(bookId) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ExportWorker.uniqueName(bookId))
    }.collectAsStateWithLifecycle(emptyList())
    val export = exportInfos.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(book?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (content != null) {
                            Text(
                                content.chapters.getOrNull(s.chapterIndex)?.title.orEmpty(),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                windowInsets = appBarInsets,
                actions = {
                    IconButton(onClick = { showChapters = true }, enabled = content != null) {
                        Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.chapters))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.voice)) }, onClick = { menu = false; showVoices = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_audio)) }, onClick = { menu = false; showExport = true }, enabled = content != null)
                            DropdownMenuItem(text = { Text(stringResource(R.string.manage_voices)) }, onClick = { menu = false; onVoices() })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (content != null) {
                PlayerBar(
                    playing = s.playWhenReady,
                    buffering = s.buffering && s.playWhenReady,
                    speed = speed,
                    progress = if (content.chunks.size > 1) s.chunkIndex.toFloat() / (content.chunks.size - 1) else 0f,
                    onPlay = pc::togglePlay,
                    onPrevChapter = pc::prevChapter,
                    onNextChapter = pc::nextChapter,
                    onPrev = pc::prevChunk,
                    onNext = pc::nextChunk,
                    onSpeed = pc::setSpeed,
                )
            }
        },
        contentWindowInsets = contentInsets,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (export != null && (export.state == WorkInfo.State.RUNNING || export.state == WorkInfo.State.ENQUEUED)) {
                val done = export.progress.getInt(ExportWorker.KEY_DONE, 0)
                val total = export.progress.getInt(ExportWorker.KEY_TOTAL, 0)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_progress, done, total), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { ExportWorker.cancel(context, bookId) }) { Text(stringResource(R.string.cancel)) }
                }
            } else if (export?.state == WorkInfo.State.FAILED) {
                Text(
                    stringResource(R.string.export_failed, export.outputData.getString(ExportWorker.KEY_ERROR).orEmpty()),
                    Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
            } else if (export?.state == WorkInfo.State.SUCCEEDED) {
                Text(stringResource(R.string.export_done), Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall)
            }

            s.error?.takeIf { book != null }?.let { err ->
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(err)
                        if (err.startsWith(PlaybackController.NO_VOICE_PREFIX)) {
                            TextButton(onClick = onVoices) { Text(stringResource(R.string.open_voices)) }
                        }
                    }
                }
            }

            if (s.slowVoice && book != null) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.slow_voice_hint), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { showVoices = true }) { Text(stringResource(R.string.voice)) }
                            TextButton(onClick = { showExport = true }, enabled = content != null) { Text(stringResource(R.string.export_audio)) }
                        }
                    }
                }
            }

            if (content == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                BookText(content, s.chunkIndex, onTap = pc::seekTo)
            }
        }
    }

    if (showChapters && content != null) {
        ChaptersDialog(content, s.chapterIndex, onPick = { pc.seekToChapter(it); showChapters = false }, onDismiss = { showChapters = false })
    }
    if (showExport && content != null) {
        ExportDialog(
            content, s.chapterIndex,
            onExport = { chapters -> exportChapters = chapters; showExport = false; exportPicker.launch(null) },
            onDismiss = { showExport = false },
        )
    }
    if (showVoices && book != null) {
        val def = c.voices.defaultFor(book.lang)
        VoicePickerDialog(
            lang = book.lang,
            selected = book.voiceId?.let(VoiceRef::decode),
            autoLabel = stringResource(R.string.auto_voice, languageName(book.lang), def?.label ?: "—"),
            onPick = { v -> pc.setBookVoice(v?.ref?.encode()); showVoices = false },
            onDismiss = { showVoices = false },
        )
    }
}

@Composable
private fun BookText(content: BookContent, current: Int, onTap: (Int) -> Unit) {
    val paragraphs = remember(content) { content.paragraphs() }
    val paraOfChunk = remember(content) {
        IntArray(content.chunks.size).also { arr -> paragraphs.forEachIndexed { p, r -> for (i in r) arr[i] = p } }
    }
    val currentPara = paraOfChunk.getOrElse(current) { 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPara)
    var follow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(currentPara, follow) {
        if (follow) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val shown = visible.any { it.index == currentPara } && visible.lastOrNull()?.index != currentPara
            if (!shown) listState.animateScrollToItem(currentPara)
        }
    }
    val highlight = MaterialTheme.colorScheme.primaryContainer

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(paragraphs, key = { i, _ -> i }) { p, range ->
                val cur = if (current in range) current else -1
                val heading = content.chunks[range.first].heading
                val text = remember(range, cur, highlight) {
                    buildAnnotatedString {
                        for (i in range) {
                            val style = if (i == cur) SpanStyle(background = highlight) else null
                            withLink(LinkAnnotation.Clickable("c$i", TextLinkStyles(style = style)) { onTap(i) }) {
                                append(content.chunks[i].text)
                            }
                            if (i != range.last) append(' ')
                        }
                    }
                }
                Text(
                    text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (heading) 12.dp else 4.dp),
                    style = if (heading) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (!follow) {
            SmallFloatingActionButton(
                onClick = { follow = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(AppIcons.MyLocation, stringResource(R.string.follow)) }
        }
    }
}

@Composable
private fun PlayerBar(
    playing: Boolean,
    buffering: Boolean,
    speed: Float,
    progress: Float,
    onPlay: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    // Background drawn under the inset area too, so the strip below the bar matches its color.
    Column(
        Modifier
            .background(BottomAppBarDefaults.containerColor)
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
    ) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        BottomAppBar(windowInsets = WindowInsets(0)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevChapter) { Icon(AppIcons.SkipPrevious, stringResource(R.string.prev_chapter)) }
                IconButton(onClick = onPrev) { Icon(AppIcons.FastRewind, stringResource(R.string.prev_sentence)) }
                FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                    when {
                        buffering -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        playing -> Icon(AppIcons.Pause, stringResource(R.string.pause))
                        else -> Icon(Icons.Default.PlayArrow, stringResource(R.string.play))
                    }
                }
                IconButton(onClick = onNext) { Icon(AppIcons.FastForward, stringResource(R.string.next_sentence)) }
                IconButton(onClick = onNextChapter) { Icon(AppIcons.SkipNext, stringResource(R.string.next_chapter)) }
                Box {
                    var speedMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { speedMenu = true }) { Text("%.1f×".format(speed)) }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        speeds.forEach { v ->
                            val selected = kotlin.math.abs(v - speed) < 0.01f
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "%.1f×".format(v),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    speedMenu = false
                                    onSpeed(v)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(content: BookContent, current: Int, onExport: (IntArray?) -> Unit, onDismiss: () -> Unit) {
    val count = content.chapters.size
    var selected by remember { mutableStateOf(setOf(current.coerceIn(0, (count - 1).coerceAtLeast(0)))) }
    val all = selected.size == count
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_audio)) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = all, onValueChange = { selected = if (it) content.chapters.indices.toSet() else emptySet() }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = all, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(stringResource(R.string.export_whole_book), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
                LazyColumn(state = state, modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(content.chapters) { i, ch ->
                        val on = i in selected
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = on, onValueChange = { selected = if (it) selected + i else selected - i }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(
                                ch.title,
                                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(if (all) null else selected.sorted().toIntArray()) }, enabled = selected.isNotEmpty()) {
                Text(pluralStringResource(R.plurals.export_n_chapters, selected.size, selected.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChaptersDialog(content: BookContent, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chapters)) },
        text = {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            LazyColumn(state = state, modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(content.chapters) { i, ch ->
                    Text(
                        ch.title,
                        Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 10.dp),
                        fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
