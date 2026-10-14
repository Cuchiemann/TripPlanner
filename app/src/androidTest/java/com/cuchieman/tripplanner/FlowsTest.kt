package com.cuchieman.tripplanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Flows F01-F09 driven through the real UI, found by text and accessibility labels
 * (the Compose version of getByRole/getByLabel). Each test starts from an empty database.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FlowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TripPlannerApplication>()
    private val repo get() = app.repository
    private val start = LocalDate.now().plusDays(7)

    @Before
    fun emptyDatabase() {
        app.database.clearAllTables()
        compose.waitUntilAtLeastOneExists(hasText("Aún no tienes viajes"), 5_000)
    }

    private fun seedTrip(title: String = "Prueba", days: Long = 3): String = runBlocking {
        repo.createTrip(title, "Lisboa", 38.72, -9.14, start, start.plusDays(days - 1))
    }

    private fun plan(id: String): TripPlan = runBlocking { repo.observePlan(id).first()!! }

    private fun openTrip(title: String) {
        compose.waitUntilAtLeastOneExists(hasContentDescription(title, substring = true), 5_000)
        assertClickablesLabelled("S01 with trips")
        compose.onNodeWithContentDescription(title, substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Resumen"), 5_000)
    }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(5_000) { condition() }

    /** Every clickable on screen must say what it does: text or a content description. */
    private fun assertClickablesLabelled(screen: String) {
        compose.waitForIdle()
        val unlabelled = compose.onAllNodes(hasClickAction(), useUnmergedTree = false)
            .fetchSemanticsNodes()
            .filter { node ->
                val c = node.config
                c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                    c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                    c.getOrNull(SemanticsActions.OnClick)?.label.isNullOrEmpty() &&
                    c.getOrNull(SemanticsProperties.EditableText) == null
            }
        assertTrue("$screen: ${unlabelled.size} clickable(s) without a label: ${unlabelled.map { it.boundsInRoot }}", unlabelled.isEmpty())
    }

    // F01 Create a first trip

    @Test
    fun f01_createTrip_withManualDestinationAndDates() {
        assertClickablesLabelled("S01 empty")
        compose.onNodeWithText("Crear viaje").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Oporto")
        compose.waitUntilAtLeastOneExists(hasText("Usar «Oporto» sin ubicación"), 5_000)
        compose.onNodeWithText("Usar «Oporto» sin ubicación").performClick()
        assertClickablesLabelled("S02")

        compose.onNodeWithText("Fechas").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Aceptar"), 5_000)
        // Day cells announce the full date ("October 20, 2026"), not just the number.
        val month = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
        compose.onAllNodes(hasText(month.withDayOfMonth(20).format(fmt), substring = true) and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasText(month.withDayOfMonth(22).format(fmt), substring = true) and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Aceptar").performClick()

        compose.onNodeWithText("Crear viaje").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Día 1 ·", substring = true), 5_000)
        val trips = runBlocking { repo.observeTripSummaries().first() }
        assertEquals(1, trips.size)
        assertEquals("Viaje a Oporto", trips.single().trip.title)
        assertEquals(3, trips.single().trip.endDate - trips.single().trip.startDate + 1)
    }

    @Test
    fun f01_withoutDates_showsAnError_andCreatesNothing() {
        compose.onNodeWithText("Crear viaje").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Oporto")
        compose.waitUntilAtLeastOneExists(hasText("Usar «Oporto» sin ubicación"), 5_000)
        compose.onNodeWithText("Usar «Oporto» sin ubicación").performClick()
        compose.onNodeWithText("Crear viaje").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Elige las fechas de ida y vuelta"), 5_000)
        assertEquals(0, runBlocking { repo.observeTripSummaries().first() }.size)
    }

    // F02 Add a place to a day

    @Test
    fun f02_addPlaceByHand_toTheFirstDay() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        assertClickablesLabelled("S05 empty")
        compose.onAllNodesWithText("Añadir lugar").onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Café Majestic")
        compose.waitUntilAtLeastOneExists(hasText("Añadir «Café Majestic» sin ubicación"), 5_000)
        compose.onNodeWithText("Añadir «Café Majestic» sin ubicación").performClick()
        waitFor { plan(id).places.isNotEmpty() }
        assertEquals(listOf("Café Majestic"), plan(id).placesOn(start).map { it.name })
        compose.waitUntilAtLeastOneExists(hasContentDescription("1. Café Majestic", substring = true), 5_000)
    }

    // F03 Reorder and delete

    @Test
    fun f03_moveUp_fromThePlaceSheet() {
        val id = seedTrip()
        runBlocking {
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa")
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Beta")
        }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onNodeWithContentDescription("2. Beta", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Subir"), 5_000)
        assertClickablesLabelled("S07")
        compose.onNodeWithText("Subir").performClick()
        waitFor { plan(id).placesOn(start).map { it.name } == listOf("Beta", "Alfa") }
    }

    @Test
    fun f03_dragAPlaceIntoTheNextDay() {
        val id = seedTrip()
        runBlocking { repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa") }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.waitForIdle()
        val dayTwo = compose.onNodeWithText("Día 2 ·", substring = true).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithContentDescription("1. Alfa", substring = true)
        val from = row.fetchSemanticsNode().boundsInRoot
        row.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            // Drop just below the "Día 2" header, in small steps like a finger would.
            val distance = dayTwo.bottom + 24f - from.center.y
            val steps = 40
            repeat(steps) {
                moveBy(Offset(0f, distance / steps))
                advanceEventTime(16)
            }
            up()
        }
        waitFor { plan(id).placesOn(start.plusDays(1)).map { it.name } == listOf("Alfa") }
        assertTrue(plan(id).placesOn(start).isEmpty())
    }

    @Test
    fun fx7_markAPlaceAsAStay_fromItsSheet() {
        val id = seedTrip()
        runBlocking { repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Piso en Alfama") }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onNodeWithContentDescription("1. Piso en Alfama", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Alojamiento"), 5_000)
        compose.onNodeWithText("Alojamiento").performClick()
        waitFor { plan(id).places.single().category == "alojamiento" }
        compose.onNodeWithText("Listo").performClick()
        // The row now says what it is, for TalkBack too.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Alojamiento", substring = true), 5_000)
    }

    @Test
    fun f03_deleteAndUndo() {
        val id = seedTrip()
        runBlocking { repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa") }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onNodeWithContentDescription("1. Alfa", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Borrar lugar"), 5_000)
        compose.onNodeWithText("Borrar lugar").performClick()
        waitFor { plan(id).places.isEmpty() }
        compose.waitUntilAtLeastOneExists(hasText("Deshacer"), 5_000)
        compose.onNodeWithText("Deshacer").performClick()
        waitFor { plan(id).places.map { it.name } == listOf("Alfa") }
    }

    // F06 Budget and expenses

    @Test
    fun f06_setBudget_thenAddExpense() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.onNodeWithText("Gastos").performClick()
        compose.onNodeWithText("Fijar presupuesto").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("500")
        compose.onNodeWithText("Guardar").performClick()
        waitFor { plan(id).trip.budgetMinor == 50_000L }

        compose.onNodeWithText("Añadir gasto", useUnmergedTree = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Nuevo gasto"), 5_000)
        assertClickablesLabelled("S10")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("20,5")
        compose.onNodeWithText("Comida").performClick()
        compose.onNodeWithText("Guardar").performClick()
        waitFor { plan(id).spentMinor == 2_050L }
        // es-ES puts a non-breaking space before the euro sign.
        compose.waitUntilAtLeastOneExists(hasText("Gastado 20,50 € de 500,00 €"), 5_000)
        assertClickablesLabelled("S09")
    }

    @Test
    fun f06_zeroAmount_isRejected() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.onNodeWithText("Gastos").performClick()
        compose.onNodeWithText("Añadir gasto", useUnmergedTree = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("0")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Introduce un importe mayor que 0"), 5_000)
        assertEquals(0, plan(id).expenses.size)
    }

    // F07 Notes survive the app going away

    @Test
    fun f07_notesAreSaved_whenTheActivityIsRecreated() {
        val id = seedTrip()
        openTrip("Prueba")
        assertClickablesLabelled("S04")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Llevar adaptador")
        compose.activityRule.scenario.recreate()
        waitFor { plan(id).trip.notes == "Llevar adaptador" }
    }

    // F09 Delete a trip

    @Test
    fun f09_deleteTrip_fromSettings() {
        seedTrip()
        openTrip("Prueba")
        compose.onNodeWithContentDescription("Ajustes del viaje").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Borrar viaje"), 5_000)
        assertClickablesLabelled("S12")
        compose.onNodeWithText("Borrar viaje").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Aún no tienes viajes"), 5_000)
        assertEquals(0, runBlocking { repo.observeTripSummaries().first() }.size)
        // No confirm dialog any more: undo instead, as for places.
        compose.waitUntilAtLeastOneExists(hasText("Deshacer"), 5_000)
        compose.onNodeWithText("Deshacer").performClick()
        waitFor { runBlocking { repo.observeTripSummaries().first() }.map { it.trip.title } == listOf("Prueba") }
    }

    // S08 Map and list on one screen

    @Test
    fun s08_mapShowsTheDaysPlacesInTheSheet() {
        val id = seedTrip()
        runBlocking {
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa", null, 38.7, -9.1)
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Sin sitio")
        }
        openTrip("Prueba")
        compose.onNodeWithContentDescription("Ver mapa").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Todos"), 5_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("1. Alfa", substring = true), 5_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Sin sitio, sin ubicación en el mapa", substring = true), 5_000)
        compose.onNodeWithContentDescription("1. Alfa", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Cómo llegar"), 5_000)
        assertClickablesLabelled("S08")
    }

    // Packing list

    @Test
    fun checklist_addAndTick_fromTheOverview() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.waitUntilAtLeastOneExists(hasText("Lista de equipaje"), 5_000)
        compose.onNode(hasSetTextAction() and hasText("Añadir a la lista…")).performTextInput("Pasaporte")
        compose.onNodeWithContentDescription("Añadir a la lista").performClick()
        waitFor { plan(id).checklist.map { it.text } == listOf("Pasaporte") }
        compose.onNodeWithText("Pasaporte").performClick()
        waitFor { plan(id).checklist.single().done }
        compose.waitUntilAtLeastOneExists(hasText("1 de 1 listos"), 5_000)
    }

    // Extra lists

    @Test
    fun lists_createOneFromTheOverview() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.onNodeWithText("Nueva lista").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Para comer")
        compose.onNodeWithText("Crear").performClick()
        waitFor { plan(id).lists.map { it.title }.contains("Para comer") }
    }

    // Several places at once

    @Test
    fun bulk_selectTwoAndMoveThemToAnotherDay() {
        val id = seedTrip()
        runBlocking {
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa")
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Beta")
            repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Gamma")
        }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onAllNodesWithText("Seleccionar").onFirst().performClick()
        compose.onNodeWithContentDescription("1. Alfa", substring = true).performClick()
        compose.onNodeWithContentDescription("3. Gamma", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("2 seleccionados"), 5_000)
        compose.onNodeWithText("Mover a…").performClick()
        // The day header behind the sheet has the same text; the sheet is the last window.
        compose.onAllNodes(hasText("Día 2 ·", substring = true) and hasClickAction()).onLast().performClick()
        waitFor { plan(id).placesOn(start.plusDays(1)).map { it.name } == listOf("Alfa", "Gamma") }
        assertEquals(listOf("Beta"), plan(id).placesOn(start).map { it.name })
    }

    // Compact view by time

    @Test
    fun compactView_showsOneLinePerPlaceWithItsTime() {
        val id = seedTrip()
        runBlocking {
            val a = repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Alfa", note = "Una nota larga")
            repo.setPlaceTime(a, 570, 660)
        }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onNodeWithContentDescription("Vista compacta por horas").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("1. Alfa, 9:30 – 11:00"), 5_000)
        compose.onNodeWithContentDescription("Vista detallada").assertExists()
        assertClickablesLabelled("S05 compact")
    }
    // Second pass: edge cases left as "todo"

    @Test
    fun f01_backMidForm_createsNothing() {
        compose.onNodeWithText("Crear viaje").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Oporto")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntilAtLeastOneExists(hasText("Aún no tienes viajes"), 5_000)
        assertEquals(0, runBlocking { repo.observeTripSummaries().first() }.size)
    }

    @Test
    fun f01_rotationMidForm_keepsWhatWasTyped() {
        compose.onNodeWithText("Crear viaje").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Oporto")
        compose.waitUntilAtLeastOneExists(hasText("Usar «Oporto» sin ubicación"), 5_000)
        compose.onNodeWithText("Usar «Oporto» sin ubicación").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Oporto"), 5_000)
        compose.onNodeWithText("Sin ubicación en el mapa").assertExists()
    }

    @Test
    fun f01_veryLongTitle_isShownWithoutBreakingTheCard() {
        val long = "Gran viaje por la costa atlántica con parada en todos los pueblos que nos apetezca"
        runBlocking { repo.createTrip(long, "Galicia", null, null, start, start.plusDays(1)) }
        compose.waitUntilAtLeastOneExists(hasContentDescription(long, substring = true), 5_000)
        compose.onNodeWithContentDescription(long, substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText(long), 5_000)
        assertClickablesLabelled("S04 long title")
    }

    @Test
    fun f05_mapWithoutLocations_saysSo() {
        val id = seedTrip()
        runBlocking { repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Sin sitio") }
        openTrip("Prueba")
        compose.onNodeWithContentDescription("Ver mapa").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ningún lugar tiene ubicación", substring = true), 5_000)
    }
    // Places the search cannot find: paste coordinates or a Google Maps link

    @Test
    fun addPlace_byPastingCoordinates() {
        val id = seedTrip()
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onAllNodesWithText("Añadir lugar").onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("38.15328, -0.6903343")
        compose.waitUntilAtLeastOneExists(hasText("Punto 38.15328, -0.69033"), 5_000)
        // A location is not a name: no "add by hand" row for it.
        assertEquals(0, compose.onAllNodes(hasText("sin ubicación", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("Punto 38.15328, -0.69033").performClick()
        waitFor { plan(id).places.singleOrNull()?.lat == 38.15328 }
    }

    @Test
    fun placeWithoutLocation_getsOneFromItsSheet() {
        val id = seedTrip()
        runBlocking { repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Ermita de Sant Francesc") }
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onNodeWithContentDescription("1. Ermita de Sant Francesc", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Sin ubicación: no aparece en el mapa"), 5_000)
        compose.onNodeWithText("Añadir").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("38.15328, -0.6903343")
        compose.onNodeWithText("Guardar").performClick()
        waitFor { plan(id).places.single().lng == -0.6903343 }
    }
    @Test
    fun addPlace_byPastingWhatTheMapsAppShares() {
        val id = seedTrip()
        // The Maps app shares "Name + link". A full place URL needs no network to read.
        val shared = "Ermita de San Francisco de Asís https://www.google.com/maps/place/Ermita+de+San+Francisco+de+As%C3%ADs/@38.15328,-0.6903343,777m/data=!3m2!1e3!4b1!8m2!3d38.15328!4d-0.6903343"
        openTrip("Prueba")
        compose.onNodeWithText("Itinerario").performClick()
        compose.onAllNodesWithText("Añadir lugar").onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(shared)
        compose.waitUntilAtLeastOneExists(hasText("Ermita de San Francisco de Asís"), 5_000)
        compose.onNodeWithText("Ermita de San Francisco de Asís").performClick()
        waitFor { plan(id).places.singleOrNull()?.let { it.name == "Ermita de San Francisco de Asís" && it.lat == 38.15328 } == true }
    }
}
