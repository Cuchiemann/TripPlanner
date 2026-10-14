package com.cuchieman.tripplanner.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuchieman.tripplanner.ui.screens.create.CreateTripScreen
import com.cuchieman.tripplanner.ui.screens.expense.ExpenseScreen
import com.cuchieman.tripplanner.ui.screens.home.HomeScreen
import com.cuchieman.tripplanner.ui.screens.map.MapScreen
import com.cuchieman.tripplanner.ui.screens.settings.TripSettingsScreen
import com.cuchieman.tripplanner.ui.screens.trip.TAB_ITINERARY
import com.cuchieman.tripplanner.ui.screens.trip.TAB_OVERVIEW
import com.cuchieman.tripplanner.ui.screens.trip.TripScreen
import java.time.LocalDate

/** Routes from replica/architecture.md. Sheets and dialogs live inside their screens. */
object Routes {
    const val TRIPS = "trips"
    const val NEW_TRIP = "trips/new"
    const val TRIP = "trip/{tripId}?tab={tab}"
    const val MAP = "trip/{tripId}/map?day={day}"
    const val EXPENSE = "trip/{tripId}/expense/{expenseId}?placeId={placeId}"
    const val SETTINGS = "trip/{tripId}/settings"

    fun trip(id: String, tab: Int = TAB_OVERVIEW) = "trip/$id?tab=$tab"
    fun map(id: String, day: LocalDate?) = "trip/$id/map?day=${day?.toEpochDay() ?: NO_DAY}"
    fun expense(id: String, expenseId: String?, placeId: String?) =
        "trip/$id/expense/${expenseId ?: NEW}" + (placeId?.let { "?placeId=$it" } ?: "")
    fun settings(id: String) = "trip/$id/settings"

    const val NEW = "new"
    const val NO_DAY = Long.MIN_VALUE
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TRIPS) {
        composable(Routes.TRIPS) {
            HomeScreen(
                onOpenTrip = { nav.navigate(Routes.trip(it)) },
                onCreateTrip = { nav.navigate(Routes.NEW_TRIP) }
            )
        }
        composable(Routes.NEW_TRIP) {
            CreateTripScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(Routes.trip(id, TAB_ITINERARY)) {
                        popUpTo(Routes.NEW_TRIP) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.TRIP,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("tab") { type = NavType.IntType; defaultValue = TAB_OVERVIEW }
            )
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            TripScreen(
                tripId = tripId,
                initialTab = entry.arguments?.getInt("tab") ?: TAB_OVERVIEW,
                onBack = { nav.popBackStack() },
                onOpenMap = { day -> nav.navigate(Routes.map(tripId, day)) },
                onOpenExpense = { expenseId, placeId -> nav.navigate(Routes.expense(tripId, expenseId, placeId)) },
                onOpenSettings = { nav.navigate(Routes.settings(tripId)) }
            )
        }
        composable(
            Routes.MAP,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("day") { type = NavType.LongType; defaultValue = Routes.NO_DAY }
            )
        ) { entry ->
            val day = entry.arguments?.getLong("day")?.takeIf { it != Routes.NO_DAY }
            MapScreen(
                tripId = entry.arguments?.getString("tripId").orEmpty(),
                initialDay = day?.let(LocalDate::ofEpochDay),
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            Routes.EXPENSE,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType },
                navArgument("placeId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val expenseId = entry.arguments?.getString("expenseId")
            ExpenseScreen(
                tripId = entry.arguments?.getString("tripId").orEmpty(),
                expenseId = expenseId?.takeIf { it != Routes.NEW },
                placeId = entry.arguments?.getString("placeId"),
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            Routes.SETTINGS,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { entry ->
            TripSettingsScreen(
                tripId = entry.arguments?.getString("tripId").orEmpty(),
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack(Routes.TRIPS, inclusive = false) }
            )
        }
    }
}
