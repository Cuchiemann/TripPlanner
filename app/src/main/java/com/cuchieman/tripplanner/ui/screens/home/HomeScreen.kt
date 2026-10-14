package com.cuchieman.tripplanner.ui.screens.home

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cuchieman.tripplanner.data.CoverStore
import com.cuchieman.tripplanner.data.DeletedTrip
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.TripSummary
import com.cuchieman.tripplanner.data.ValidationException
import com.cuchieman.tripplanner.data.createSampleTrip
import com.cuchieman.tripplanner.ui.appViewModel
import com.cuchieman.tripplanner.ui.components.EmptyState
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpExtendedFab
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.components.TripCard
import com.cuchieman.tripplanner.ui.formatRange
import com.cuchieman.tripplanner.ui.rememberCoverBitmap
import com.cuchieman.tripplanner.ui.theme.TpColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HomeViewModel(
    private val repository: TripRepository,
    /** Set by the trip settings when a trip is deleted; cleared here once undone or gone. */
    val deletedTrip: MutableStateFlow<DeletedTrip?>,
    private val covers: CoverStore,
) : ViewModel() {
    fun undoDelete(deleted: DeletedTrip) {
        deletedTrip.value = null
        viewModelScope.launch { repository.undoDeleteTrip(deleted) }
    }

    /** The snackbar went away without undo: the trip is gone for good, and so is its photo. */
    fun forget(deleted: DeletedTrip) {
        if (deletedTrip.value == deleted) deletedTrip.value = null
        viewModelScope.launch { covers.delete(deleted.coverPath) }
    }

    /** Null while the first query runs. */
    val trips: StateFlow<List<TripSummary>?> = repository.observeTripSummaries()
        .map<List<TripSummary>, List<TripSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createSample(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createSampleTrip()) }
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Reads a backup file the user picked and adds it as a new trip (fixes.md #5). */
    fun importFrom(uri: Uri, resolver: ContentResolver, onImported: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("No input stream for $uri")
                    input.use { stream ->
                        // A trip is a few kilobytes; anything huge is not one of ours.
                        val bytes = stream.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                        if (bytes.size > MAX_IMPORT_BYTES) throw ValidationException("El archivo es demasiado grande para ser un viaje")
                        String(bytes, Charsets.UTF_8)
                    }
                }
                repository.importTrip(text)
            }.onSuccess(onImported)
                .onFailure { e -> _messages.send((e as? ValidationException)?.message ?: "No se ha podido abrir el archivo") }
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }
}

/** InputStream.readNBytes is API 33+; this reads up to [max] bytes on any version. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** S01: trip list, split into upcoming and past. */
@Composable
fun HomeScreen(
    onOpenTrip: (String) -> Unit,
    onCreateTrip: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository, it.deletedTrip, it.covers) }
    val deleted by vm.deletedTrip.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val list = trips
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(deleted) {
        val trip = deleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Viaje borrado: ${trip.title}", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(trip) else vm.forget(trip)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri, context.contentResolver, onOpenTrip)
    }
    // Some file managers label JSON as text or as a generic file, so accept those too.
    val openBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    Scaffold(
        topBar = {
            TpTopBar("Mis viajes", actions = {
                IconButton(onClick = openBackup) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "Importar viaje")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!list.isNullOrEmpty()) TpExtendedFab("Nuevo viaje", Icons.Filled.Add, onCreateTrip)
        },
        containerColor = TpColors.Bg
    ) { padding ->
        when {
            // Room answers in milliseconds; a blank frame beats a flashing spinner.
            list == null -> Box(Modifier.fillMaxSize().padding(padding))

            list.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyState(
                    icon = Icons.Filled.Flight,
                    title = "Aún no tienes viajes",
                    message = "Crea uno y empieza a repartir los planes por días.",
                    actionLabel = "Crear viaje",
                    onAction = onCreateTrip
                )
                TpButton(
                    "Probar con un viaje de ejemplo",
                    onClick = { vm.createSample(onOpenTrip) },
                    variant = TpButtonVariant.Text
                )
                TpButton("Importar un viaje", onClick = openBackup, variant = TpButtonVariant.Text)
            }

            else -> {
                val upcoming = list.filter { !it.end.isBefore(today) }.sortedBy { it.trip.startDate }
                val past = list.filter { it.end.isBefore(today) }.sortedByDescending { it.trip.startDate }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (upcoming.isNotEmpty()) {
                        item(key = "h-upcoming") { GroupLabel("Próximos") }
                        items(upcoming, key = { it.trip.id }) { TripItem(it, onOpenTrip, past = false) }
                    }
                    if (past.isNotEmpty()) {
                        item(key = "h-past") { GroupLabel("Pasados") }
                        items(past, key = { it.trip.id }) { TripItem(it, onOpenTrip, past = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripItem(summary: TripSummary, onOpen: (String) -> Unit, past: Boolean) {
    TripCard(
        title = summary.trip.title,
        destination = summary.trip.destinationName,
        dates = formatRange(summary.start, summary.end, withLength = false),
        placeCount = summary.placeCount,
        onClick = { onOpen(summary.trip.id) },
        past = past,
        cover = rememberCoverBitmap(summary.trip.coverUri).value
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = TpColors.TextMuted
    )
}
