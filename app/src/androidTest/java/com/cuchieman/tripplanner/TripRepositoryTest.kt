package com.cuchieman.tripplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.ValidationException
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import com.cuchieman.tripplanner.ui.dayDistanceKm
import com.cuchieman.tripplanner.ui.haversineKm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Data rules behind flows F01-F09, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class TripRepositoryTest {
    private lateinit var db: TripPlannerDatabase
    private lateinit var repo: TripRepository
    private val start = LocalDate.of(2026, 5, 3)
    private val end = LocalDate.of(2026, 5, 6) // 4 days

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TripPlannerDatabase::class.java
        ).build()
        repo = TripRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun plan(id: String): TripPlan = runBlocking { repo.observePlan(id).first()!! }
    private fun day(n: Int) = PlaceTarget.Day(start.plusDays(n.toLong() - 1).toEpochDay())
    private fun names(plan: TripPlan, n: Int) = plan.placesOn(start.plusDays(n.toLong() - 1)).map { it.name }

    private fun <T> assertFails(block: suspend () -> T) {
        val failed = runCatching { runBlocking { block() } }.exceptionOrNull()
        assertTrue("expected a ValidationException, got $failed", failed is ValidationException)
    }

    // F01

    @Test
    fun f01_createTrip_addsDefaultListAndDefaultTitle() = runBlocking {
        val id = repo.createTrip("", "Oporto", null, null, start, end)
        val p = plan(id)
        assertEquals("Viaje a Oporto", p.trip.title)
        assertEquals(4, p.days.size)
        assertEquals(listOf(TripRepository.DEFAULT_LIST_TITLE), p.lists.map { it.title })
        assertTrue(p.lists.single().isDefault)
    }

    @Test
    fun f01_rejectsReturnBeforeDeparture() = assertFails { repo.createTrip("x", "Oporto", null, null, end, start) }

    @Test
    fun f01_rejectsTripsLongerThan90Days() =
        assertFails { repo.createTrip("x", "Oporto", null, null, start, start.plusDays(90)) }

    @Test
    fun f01_acceptsExactly90DaysAndOneDayTrips() = runBlocking {
        repo.createTrip("x", "A", null, null, start, start.plusDays(89))
        repo.createTrip("y", "B", null, null, start, start)
        Unit
    }

    @Test
    fun f01_rejectsBlankDestination() = assertFails { repo.createTrip("x", "  ", null, null, start, end) }

    // F02

    @Test
    fun f02_placesAppendInOrder_andKeepAccentsAndEmoji() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.addPlace(id, day(1), "Café A Brasileira ☕")
        repo.addPlace(id, day(1), "Elevador de Santa Justa")
        assertEquals(listOf("Café A Brasileira ☕", "Elevador de Santa Justa"), names(plan(id), 1))
        assertEquals(listOf(0, 1), plan(id).placesOn(start).map { it.position })
    }

    @Test
    fun f02_rejectsDayOutsideTrip() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        assertFails { repo.addPlace(id, PlaceTarget.Day(end.plusDays(1).toEpochDay()), "Fuera") }
    }

    @Test
    fun f02_rejectsBlankNameAndHalfCoordinates() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        assertFails { repo.addPlace(id, day(1), "   ") }
        assertFails { repo.addPlace(id, day(1), "A", lat = 1.0, lng = null) }
    }

    // F03

    @Test
    fun f03_moveAcrossDays_renumbersBothDays() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        repo.addPlace(id, day(1), "B")
        repo.addPlace(id, day(1), "C")
        repo.addPlace(id, day(2), "D")
        repo.movePlace(a, day(2), 1)
        val p = plan(id)
        assertEquals(listOf("B", "C"), names(p, 1))
        assertEquals(listOf("D", "A"), names(p, 2))
        assertEquals(listOf(0, 1), p.placesOn(start).map { it.position })
        assertEquals(listOf(0, 1), p.placesOn(start.plusDays(1)).map { it.position })
    }

    @Test
    fun f03_nudgeUpAndDown() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.addPlace(id, day(1), "A")
        val b = repo.addPlace(id, day(1), "B")
        repo.addPlace(id, day(1), "C")
        repo.nudgePlace(b, -1)
        assertEquals(listOf("B", "A", "C"), names(plan(id), 1))
        repo.nudgePlace(b, 1)
        repo.nudgePlace(b, 1)
        assertEquals(listOf("A", "C", "B"), names(plan(id), 1))
        repo.nudgePlace(b, 1) // already last: stays
        assertEquals(listOf("A", "C", "B"), names(plan(id), 1))
    }

    @Test
    fun f03_moveToList_dropsTheTime() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        repo.setPlaceTime(a, 600, 660)
        repo.movePlace(a, PlaceTarget.InList(repo.defaultListId(id)))
        val moved = plan(id).place(a)!!
        assertNull(moved.startMinute)
        assertNull(moved.endMinute)
    }

    @Test
    fun f03_deleteThenUndo_restoresSamePosition() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.addPlace(id, day(1), "A")
        val b = repo.addPlace(id, day(1), "B")
        repo.addPlace(id, day(1), "C")
        val removed = repo.deletePlace(b)!!
        assertEquals(listOf("A", "C"), names(plan(id), 1))
        assertEquals(listOf(0, 1), plan(id).placesOn(start).map { it.position })
        repo.restorePlace(removed)
        assertEquals(listOf("A", "B", "C"), names(plan(id), 1))
    }

    // fixes.md #7: a type on places added by hand

    @Test
    fun fx7_placeType_canBeSetChangedAndCleared() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "Apartamento en Alfama")
        repo.setPlaceCategory(a, "alojamiento")
        assertEquals("alojamiento", plan(id).place(a)!!.category)
        repo.setPlaceCategory(a, "comida")
        assertEquals("comida", plan(id).place(a)!!.category)
        repo.setPlaceCategory(a, null)
        assertNull(plan(id).place(a)!!.category)
    }

    @Test
    fun fx7_unknownPlaceType_isRejected() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        assertFails { repo.setPlaceCategory(a, "casino") }
    }

    // Extra lists

    @Test
    fun lists_createRenameDelete_andTheDefaultListStays() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val food = repo.createList(id, "Para comer")
        repo.addPlace(id, PlaceTarget.InList(food), "Tasca")
        repo.renameList(food, "Comer bien")
        assertEquals(listOf(TripRepository.DEFAULT_LIST_TITLE, "Comer bien"), plan(id).lists.map { it.title })
        assertFails { repo.createList(id, "  ") }
        assertFails { repo.deleteList(repo.defaultListId(id)) }
        repo.deleteList(food)
        assertEquals(1, plan(id).lists.size)
        assertTrue(plan(id).places.isEmpty())
    }

    // Several places at once

    @Test
    fun bulk_moveKeepsTheirOrder_deleteCanBeUndone() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        repo.addPlace(id, day(1), "B")
        val c = repo.addPlace(id, day(1), "C")
        repo.addPlace(id, day(2), "D")
        repo.movePlaces(listOf(c, a), day(2))
        assertEquals(listOf("B"), names(plan(id), 1))
        assertEquals(listOf("D", "A", "C"), names(plan(id), 2))

        val deleted = repo.deletePlaces(listOf(a, c))
        assertEquals(listOf("D"), names(plan(id), 2))
        repo.restorePlaces(deleted)
        assertEquals(listOf("D", "A", "C"), names(plan(id), 2))
    }

    // Checklist

    @Test
    fun checklist_addTickDeleteUndo() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val passport = repo.addChecklistItem(id, "Pasaporte")
        repo.addChecklistItem(id, "Adaptador de enchufe")
        repo.setChecklistDone(passport, true)
        assertEquals(listOf("Pasaporte" to true, "Adaptador de enchufe" to false), plan(id).checklist.map { it.text to it.done })
        assertFails { repo.addChecklistItem(id, " ") }
        val removed = repo.deleteChecklistItem(passport)!!
        assertEquals(1, plan(id).checklist.size)
        repo.restoreChecklistItem(removed)
        assertEquals(listOf("Pasaporte", "Adaptador de enchufe"), plan(id).checklist.map { it.text })
    }

    // Undo for a deleted trip

    @Test
    fun deletedTrip_comesBackWithItsPlacesAndCover() = runBlocking {
        val id = repo.createTrip("Volver", "Lisboa", null, null, start, end)
        repo.addPlace(id, day(1), "A")
        repo.addChecklistItem(id, "Pasaporte")
        repo.setCover(id, "/data/covers/x.jpg")
        val deleted = repo.deleteTripForUndo(id)!!
        assertNull(repo.observePlan(id).first())
        val back = repo.undoDeleteTrip(deleted)
        val p = plan(back)
        assertEquals("Volver", p.trip.title)
        assertEquals(listOf("A"), names(p, 1))
        assertEquals(listOf("Pasaporte"), p.checklist.map { it.text })
        assertEquals("/data/covers/x.jpg", p.trip.coverUri)
    }

    // fixes.md #8: total distance per day

    @Test
    fun fx8_dayDistance_sumsConsecutiveLocatedStops() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.addPlace(id, day(1), "A", lat = 38.6916, lng = -9.2160)
        repo.addPlace(id, day(1), "Sin ubicación")
        repo.addPlace(id, day(1), "B", lat = 38.6979, lng = -9.2065)
        repo.addPlace(id, day(1), "C", lat = 38.7139, lng = -9.1335)
        repo.addPlace(id, day(2), "Solo", lat = 38.7, lng = -9.1)
        val p = plan(id)
        val expected = haversineKm(38.6916, -9.2160, 38.6979, -9.2065) + haversineKm(38.6979, -9.2065, 38.7139, -9.1335)
        assertEquals(expected, dayDistanceKm(p.placesOn(start))!!, 1e-9)
        assertNull(dayDistanceKm(p.placesOn(start.plusDays(1))))
        assertNull(dayDistanceKm(p.placesOn(start.plusDays(2))))
    }

    // F04

    @Test
    fun f04_endBeforeStartIsRejected() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        assertFails { repo.setPlaceTime(a, 600, 540) }
        assertFails { repo.setPlaceTime(a, null, 540) }
        repo.setPlaceTime(a, 600, 600)
        assertEquals(600, plan(id).place(a)!!.endMinute)
    }

    // F06

    @Test
    fun f06_expenseValidation() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        assertFails { repo.saveExpense(null, id, 0, "comida", "", null, null) }
        assertFails { repo.saveExpense(null, id, 100, "casino", "", null, null) }
        assertFails { repo.setBudget(id, -5) }
        repo.saveExpense(null, id, 1250, "comida", "Cena", null, null)
        assertEquals(1250L, plan(id).spentMinor)
    }

    @Test
    fun f06_editingAnExpenseUpdatesInsteadOfDuplicating() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val e = repo.saveExpense(null, id, 1000, "comida", "Cena", null, null)
        repo.saveExpense(e, id, 1500, "comida", "Cena", null, null)
        assertEquals(1, plan(id).expenses.size)
        assertEquals(1500L, plan(id).spentMinor)
    }

    @Test
    fun f06_deletingAPlaceKeepsItsExpense() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(1), "A")
        repo.saveExpense(null, id, 1000, "actividades", "Entrada", a, null)
        repo.deletePlace(a)
        val expense = plan(id).expenses.single()
        assertNull(expense.placeId)
        assertEquals(1000L, expense.amountMinor)
    }

    // F07

    @Test
    fun f07_notesAreSaved() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.updateNotes(id, "Llevar adaptador ñ 🔌")
        assertEquals("Llevar adaptador ñ 🔌", plan(id).trip.notes)
    }

    // F08

    @Test
    fun f08_shiftKeepsDayNumberAndTimes() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        val a = repo.addPlace(id, day(2), "A")
        repo.setPlaceTime(a, 600, null)
        val moved = repo.changeDates(id, start.plusDays(10), end.plusDays(10))
        assertEquals(0, moved)
        val p = plan(id)
        assertEquals(1, p.dayIndexOf(p.place(a)!!))
        assertEquals(600, p.place(a)!!.startMinute)
    }

    @Test
    fun f08_shorterTripMovesPlacesToDefaultListInOrder_neverDeletes() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, end)
        repo.addPlace(id, PlaceTarget.InList(repo.defaultListId(id)), "Ya en la lista")
        repo.addPlace(id, day(1), "D1")
        repo.addPlace(id, day(3), "D3a")
        repo.addPlace(id, day(3), "D3b")
        repo.addPlace(id, day(4), "D4")
        val moved = repo.changeDates(id, start, start.plusDays(1)) // 2 days now
        assertEquals(3, moved)
        val p = plan(id)
        assertEquals(5, p.places.size)
        assertEquals(listOf("D1"), names(p, 1))
        val list = p.placesIn(repo.defaultListId(id))
        assertEquals(listOf("Ya en la lista", "D3a", "D3b", "D4"), list.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), list.map { it.position })
    }

    // F09

    @Test
    fun f09_deletingATripRemovesEverythingOfIt_only() = runBlocking {
        val keep = repo.createTrip("keep", "Roma", null, null, start, end)
        val gone = repo.createTrip("gone", "Lisboa", null, null, start, end)
        repo.addPlace(keep, day(1), "Coliseo")
        val a = repo.addPlace(gone, day(1), "A")
        repo.saveExpense(null, gone, 100, "otros", "", a, null)
        repo.deleteTrip(gone)
        assertNull(repo.observePlan(gone).first())
        assertEquals(listOf("Coliseo"), plan(keep).places.map { it.name })
        assertEquals(1, repo.observeTripSummaries().first().size)
    }
}
