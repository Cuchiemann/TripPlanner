package com.cuchieman.tripplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuchieman.tripplanner.TripPlannerApplication

/** ViewModel with its dependencies taken from the Application (manual DI, like TamaTask). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (TripPlannerApplication) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as TripPlannerApplication
    return viewModel(factory = viewModelFactory { initializer { create(app) } })
}

/** Something to tell the user in a snackbar, optionally with an action such as "Deshacer". */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
