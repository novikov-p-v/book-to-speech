package app.booktts.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.booktts.R
import app.booktts.container
import app.booktts.data.Book
import app.booktts.voice.languageName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    pendingImport: MutableStateFlow<Uri?>,
    onOpen: (String) -> Unit,
    onVoices: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val books by c.library.books.collectAsStateWithLifecycle()
    val installedVoices by c.voices.installed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Book?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    fun import(uri: Uri) {
        importing = true
        scope.launch {
            try {
                val book = c.library.import(uri)
                onOpen(book.id)
            } catch (e: Exception) {
                snackbar.showSnackbar(context.getString(R.string.import_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                importing = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            import(uri)
        }
    }

    val pending by pendingImport.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            pendingImport.value = null
            import(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library)) },
                windowInsets = appBarInsets,
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
                    }
                    IconButton(onClick = onVoices) {
                        Icon(AppIcons.RecordVoiceOver, contentDescription = stringResource(R.string.voices))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!importing) picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_book)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = contentInsets,
    ) { padding ->
        Column(Modifier.padding(padding.withoutBottom()).fillMaxSize()) {
            if (importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.importing), Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (installedVoices.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.no_voices_hint), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onVoices) { Text(stringResource(R.string.open_voices)) }
                }
                HorizontalDivider()
            }
            if (books.isEmpty() && !importing) {
                Box(Modifier.fillMaxSize().padding(padding.bottomOnly()).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_library), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.bottomOnly(88.dp)) {
                    items(books.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }, key = { it.id }) { book ->
                        BookRow(book, onClick = { onOpen(book.id) }, onDelete = { toDelete = book })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    toDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_book)) },
            text = { Text(book.title) },
            confirmButton = {
                TextButton(onClick = {
                    c.playback.close(book.id)
                    c.library.delete(book.id)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                book.author.takeIf { it.isNotEmpty() },
                book.format.uppercase(),
                languageName(book.lang),
                "${(book.progress * 100).toInt()}%",
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth(0.6f))
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
    }
}
