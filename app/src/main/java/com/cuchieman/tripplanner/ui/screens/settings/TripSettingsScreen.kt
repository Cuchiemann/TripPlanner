package com.cuchieman.tripplanner.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuchieman.tripplanner.ui.appViewModel

import com.cuchieman.tripplanner.ui.components.DateRangeField
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpDateRangePickerDialog
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.dateToUtcMillis
import com.cuchieman.tripplanner.ui.formatRange
import com.cuchieman.tripplanner.ui.printTrip
import com.cuchieman.tripplanner.ui.rememberCoverBitmap
import com.cuchieman.tripplanner.ui.shareTripText
import com.cuchieman.tripplanner.ui.screens.trip.TripUiState
import com.cuchieman.tripplanner.ui.screens.trip.TripViewModel
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import com.cuchieman.tripplanner.ui.utcMillisToDate

/** S12: rename, change dates, delete. */
@Composable
fun TripSettingsScreen(
    tripId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val vm = appViewModel { TripViewModel(tripId, it.repository, it.geocoding, it.appScope, it.covers, it.deletedTrip) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDates by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportTo(uri, context.contentResolver) }
    val kmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { uri -> if (uri != null) vm.exportKmlTo(uri, context.contentResolver) }
    // The system photo picker: no storage permission needed.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCover(uri)
    }
    val coverPath = (state as? TripUiState.Ready)?.plan?.trip?.coverUri
    val cover by rememberCoverBitmap(coverPath)

    Scaffold(
        topBar = { TpTopBar("Ajustes del viaje", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = TpColors.Bg
    ) { padding ->
        val ready = state as? TripUiState.Ready
        if (ready == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val plan = ready.plan
        var title by remember(plan.trip.id) { mutableStateOf(plan.trip.title) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre del viaje") },
                isError = title.isBlank(),
                singleLine = true,
                shape = RoundedCornerShape(TpRadius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TpColors.Accent,
                    unfocusedBorderColor = TpColors.BorderInput
                )
            )
            TpButton(
                "Guardar nombre",
                onClick = { vm.rename(title) },
                variant = TpButtonVariant.Secondary,
                enabled = title.isNotBlank() && title.trim() != plan.trip.title
            )

            DateRangeField(text = formatRange(plan.start, plan.end), onClick = { showDates = true })
            Text(
                "Si acortas el viaje, los lugares de los días que sobran pasan a «Lugares por visitar». No se borra nada.",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextMuted
            )

            Spacer(Modifier.height(8.dp))
            Text("Foto de portada", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
            cover?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Foto de portada actual",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(TpRadius.lg)),
                    contentScale = ContentScale.Crop
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TpButton(
                    if (plan.trip.coverUri == null) "Elegir foto" else "Cambiar foto",
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    variant = TpButtonVariant.Secondary,
                    icon = Icons.Filled.Image
                )
                if (plan.trip.coverUri != null) {
                    TpButton("Quitar foto", onClick = vm::clearCover, variant = TpButtonVariant.Text)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Copia y compartir", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
            TpButton(
                "Exportar a un archivo",
                onClick = { exportLauncher.launch(backupFileName(plan.trip.title)) },
                modifier = Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Secondary,
                icon = Icons.Filled.Save
            )
            TpButton(
                "Compartir como texto",
                onClick = { shareTripText(context, plan) },
                modifier = Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Secondary,
                icon = Icons.Filled.Share
            )
            TpButton(
                "Imprimir o guardar como PDF",
                onClick = { printTrip(context, plan) },
                modifier = Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Secondary,
                icon = Icons.Filled.Print
            )
            TpButton(
                "Exportar para Google My Maps (KML)",
                onClick = { kmlLauncher.launch(backupFileName(plan.trip.title).removeSuffix(".json") + ".kml") },
                modifier = Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Secondary,
                icon = Icons.Filled.Map
            )
            Text(
                "El archivo .json guarda el viaje entero: ábrelo en este u otro móvil con «Importar viaje» en la pantalla de inicio. " +
                    "El .kml lleva los lugares con ubicación, por días, para importarlos en Google My Maps.",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextMuted
            )

            Spacer(Modifier.height(24.dp))
            TpButton(
                "Borrar viaje",
                // No confirm dialog: the home screen offers "Deshacer", as for places.
                onClick = { vm.deleteTrip(onDeleted) },
                modifier = Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Danger,
                icon = Icons.Filled.Delete
            )
        }

        if (showDates) {
            TpDateRangePickerDialog(
                onDismiss = { showDates = false },
                onConfirm = { s, e ->
                    vm.changeDates(utcMillisToDate(s), utcMillisToDate(e))
                    showDates = false
                },
                initialStartMillis = dateToUtcMillis(plan.start),
                initialEndMillis = dateToUtcMillis(plan.end)
            )
        }
    }
}

/** "Lisboa en cuatro días.json", with characters file systems dislike replaced. */
private fun backupFileName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().ifBlank { "viaje" }.take(60) + ".json"
