package app.booktts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import android.content.pm.ApplicationInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.booktts.R
import app.booktts.container
import app.booktts.voice.CatalogModel
import app.booktts.voice.ModelType
import app.booktts.voice.VoiceDownloadWorker
import app.booktts.voice.languageName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val installed by c.voices.installed.collectAsStateWithLifecycle()
    val defaults by c.settings.defaultVoices.collectAsStateWithLifecycle()
    val books by c.library.books.collectAsStateWithLifecycle()
    val steps by c.settings.qualityStepsFlow.collectAsStateWithLifecycle()
    val works by remember {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(VoiceDownloadWorker.TAG)
    }.collectAsStateWithLifecycle(emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var pickLang by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { c.preview.close() } }

    LaunchedEffect(works) {
        if (works.any { it.state == WorkInfo.State.SUCCEEDED }) {
            c.voices.refresh()
            c.playback.refreshVoice()
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    c.voices.importArchive(uri)
                } catch (e: Exception) {
                    snackbar.showSnackbar(context.getString(R.string.import_failed, e.message ?: ""))
                } finally {
                    importing = false
                }
            }
        }
    }

    // Languages worth a "default voice" row: those of your books, ru/en, and single-language models.
    val defaultLangs = remember(installed, books) {
        val covered = installed.flatMap { it.meta.langs }.toSet()
        (listOf("ru", "en") + books.map { it.lang } + installed.filter { it.meta.langs.size == 1 }.flatMap { it.meta.langs })
            .distinct().filter { it in covered }
    }
    val catalogIds = c.voices.catalog.map { it.id }.toSet()
    val featured = c.voices.catalog.filter { it.type != ModelType.VITS }
    val piperByLang = c.voices.catalog.filter { it.type == ModelType.VITS }.groupBy { it.langs.first() }.toList()
        .sortedWith(compareBy({ listOf("ru", "en").indexOf(it.first).let { i -> if (i < 0) 99 else i } }, { it.first }))
    val imported = installed.filter { it.id !in catalogIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voices)) },
                windowInsets = appBarInsets,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                        Icon(AppIcons.FileOpen, stringResource(R.string.import_voice))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = contentInsets,
    ) { padding ->
        LazyColumn(Modifier.padding(padding.withoutBottom()).fillMaxSize(), contentPadding = padding.bottomOnly()) {
            item {
                Text(
                    stringResource(R.string.voices_hint),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (defaultLangs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.default_voices)) }
                items(defaultLangs, key = { "d_$it" }) { lang ->
                    val current = defaults.let { c.voices.defaultFor(lang) } // re-read when defaults change
                    Column(
                        Modifier.fillMaxWidth().clickable { pickLang = lang }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(languageName(lang), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            current?.label ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.ru_pronunciation))
                YoRow()
                if (c.espeakPatch.available) StressDictRow()
            }

            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable && defaultLangs.isNotEmpty()) {
                item { CustomTextRow(defaultLangs) }
            }

            if (installed.any { it.type == ModelType.SUPERTONIC }) {
                item {
                    SectionHeader(stringResource(R.string.supertonic_quality))
                    val opts = listOf(3 to R.string.quality_fast, 5 to R.string.quality_balanced, 8 to R.string.quality_best)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        opts.forEachIndexed { i, (n, label) ->
                            SegmentedButton(
                                selected = steps == n,
                                onClick = { c.settings.qualitySteps = n },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(
                        stringResource(R.string.supertonic_quality_hint),
                        Modifier.padding(16.dp, 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.best_quality_models)) }
            items(featured, key = { it.id }) { m -> ModelRow(m, installed.any { it.id == m.id }, works) }

            for ((lang, models) in piperByLang) {
                item(key = "h_$lang") { SectionHeader("Piper — ${languageName(lang)}") }
                items(models, key = { it.id }) { m -> ModelRow(m, installed.any { it.id == m.id }, works) }
            }

            if (imported.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.imported_voices)) }
                items(imported, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, Modifier.weight(1f))
                        IconButton(onClick = { c.voices.delete(m.id); c.playback.refreshVoice() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    pickLang?.let { lang ->
        VoicePickerDialog(
            lang = lang,
            selected = c.voices.defaultFor(lang)?.ref,
            autoLabel = null,
            onPick = { v ->
                if (v != null) c.settings.setDefaultVoice(lang, v.ref.encode())
                c.playback.refreshVoice()
                pickLang = null
            },
            onDismiss = { pickLang = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ModelRow(m: CatalogModel, isInstalled: Boolean, works: List<WorkInfo>) {
    val context = LocalContext.current
    val c = context.container
    val tag = VoiceDownloadWorker.tagFor(m.id)
    val work = works.firstOrNull { tag in it.tags && !it.state.isFinished }
    val failed = works.firstOrNull { tag in it.tags && it.state == WorkInfo.State.FAILED }
        ?.takeIf { !isInstalled && work == null }

    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(m.name.removePrefix("Piper "), style = MaterialTheme.typography.bodyLarge)
            val details = buildString {
                append("${m.sizeBytes / 1_000_000} MB")
                if (m.license.isNotEmpty()) append(" · ").append(m.license)
                if (m.speakers.isNotEmpty()) append(" · ${m.speakers.size} ").append(context.getString(R.string.speakers))
            }
            val sub = when {
                work != null -> {
                    if (work.progress.getString(VoiceDownloadWorker.KEY_STAGE) == "extract") stringResource(R.string.installing)
                    else stringResource(R.string.downloading_pct, work.progress.getInt(VoiceDownloadWorker.KEY_PROGRESS, 0))
                }
                isInstalled -> stringResource(R.string.installed) + " · " + details
                failed != null -> stringResource(R.string.download_failed, failed.outputData.getString(VoiceDownloadWorker.KEY_ERROR).orEmpty())
                else -> details
            }
            Text(
                sub, style = MaterialTheme.typography.bodySmall,
                color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (work != null) {
                val pct = work.progress.getInt(VoiceDownloadWorker.KEY_PROGRESS, 0)
                if (work.progress.getString(VoiceDownloadWorker.KEY_STAGE) == "extract" || pct == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        when {
            work != null -> IconButton(onClick = { VoiceDownloadWorker.cancel(context, m.id) }) {
                Icon(Icons.Default.Close, stringResource(R.string.cancel))
            }
            isInstalled -> IconButton(onClick = { c.voices.delete(m.id); c.playback.refreshVoice() }) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete))
            }
            else -> IconButton(onClick = { VoiceDownloadWorker.enqueue(context, m.id) }) {
                Icon(AppIcons.Download, stringResource(R.string.download))
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

/** Debug builds only: speak any text with the default voice of a language, through the full speech pipeline. */
@Composable
private fun CustomTextRow(langs: List<String>) {
    val c = LocalContext.current.container
    val active by c.preview.active.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(langs.first()) }
    val voice = c.voices.defaultFor(lang)
    SectionHeader("Speak custom text (debug)")
    if (langs.size > 1) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            langs.forEachIndexed { i, l ->
                SegmentedButton(
                    selected = lang == l,
                    onClick = { lang = l },
                    shape = SegmentedButtonDefaults.itemShape(i, langs.size),
                ) { Text(l) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text(voice?.label ?: "—") },
            maxLines = 4,
        )
        val playing = voice != null && active == c.preview.keyOf(voice, lang)
        IconButton(
            enabled = voice != null && text.isNotBlank(),
            onClick = { if (playing) c.preview.stop() else c.preview.play(voice!!, lang, text) },
        ) {
            Icon(if (playing) AppIcons.Pause else Icons.Default.PlayArrow, stringResource(R.string.play))
        }
    }
}

@Composable
private fun YoRow() {
    val c = LocalContext.current.container
    val enabled by c.settings.restoreYoFlow.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().clickable { c.settings.restoreYo = !enabled }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ru_yo_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.ru_yo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { c.settings.restoreYo = it })
    }
}

@Composable
private fun StressDictRow() {
    val c = LocalContext.current.container
    val enabled by c.settings.ruStressDictFlow.collectAsStateWithLifecycle()
    var changed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun set(v: Boolean) {
        c.settings.ruStressDict = v
        changed = !changed
    }
    Row(
        Modifier.fillMaxWidth().clickable { set(!enabled) }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ru_stress_dict_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (changed) R.string.restart_to_apply else R.string.ru_stress_dict_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { set(it) })
    }
}
