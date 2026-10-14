package com.cuchieman.tripplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.ValidationException
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import com.cuchieman.tripplanner.ui.toShareText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** fixes.md #5: export a trip to a file, import it back, share it as text. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private lateinit var db: TripPlannerDatabase
    private lateinit var repo: TripRepository
    private val start = LocalDate.of(2026, 5, 3)

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

    /** Everything a user would notice, without ids or timestamps. */
    private fun shape(p: TripPlan): List<Any?> = listOf(
        p.trip.title, p.trip.destinationName, p.trip.destinationLat, p.trip.destinationLng,
        p.trip.startDate, p.trip.endDate, p.trip.notes, p.trip.budgetMinor, p.trip.currency,
        p.lists.map { it.title to it.isDefault },
        p.days.map { d -> p.placesOn(d).map { listOf(it.name, it.address, it.lat, it.lng, it.note, it.startMinute, it.endMinute, it.category) } },
        p.lists.map { l -> p.placesIn(l.id).map { it.name } },
        p.checklist.map { it.text to it.done },
        p.expenses.map { e -> listOf(e.amountMinor, e.category, e.description, e.date, e.placeId?.let { id -> p.place(id)?.name }) }
            .sortedBy { it.toString() },
    )

    private fun sampleTrip(): String = runBlocking {
        val id = repo.createTrip("Oporto, comillas \"y\" saltos\nde línea", "Oporto", 41.15, -8.61, start, start.plusDays(2))
        repo.updateNotes(id, "Llevar paraguas ☔\nY el cargador")
        repo.setBudget(id, 60_000)
        val a = repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Livraria Lello", "Rua das Carmelitas", 41.1469, -8.6149, "Reservar entrada")
        repo.setPlaceTime(a, 600, 690)
        val torre = repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Torre dos Clérigos")
        repo.setPlaceCategory(torre, "actividades")
        val pass = repo.addChecklistItem(id, "Pasaporte")
        repo.setChecklistDone(pass, true)
        repo.addChecklistItem(id, "Paraguas")
        repo.addPlace(id, PlaceTarget.Day(start.plusDays(2).toEpochDay()), "Ribeira")
        repo.addPlace(id, PlaceTarget.InList(repo.defaultListId(id)), "Bodega de vino de Oporto")
        repo.saveExpense(null, id, 800, "actividades", "Entrada librería", a, start)
        repo.saveExpense(null, id, 4_500, "comida", "Francesinha", null, null)
        id
    }

    @Test
    fun exportThenImport_givesTheSameTripAsANewOne() = runBlocking {
        val original = sampleTrip()
        val json = repo.exportTrip(original)
        val copy = repo.importTrip(json)
        assertNotEquals(original, copy)
        assertEquals(shape(plan(original)), shape(plan(copy)))
        assertEquals(2, repo.observeTripSummaries().first().size)
    }

    @Test
    fun importingTwice_makesTwoIndependentTrips() = runBlocking {
        val json = repo.exportTrip(sampleTrip())
        val one = repo.importTrip(json)
        val two = repo.importTrip(json)
        assertNotEquals(one, two)
        repo.deleteTrip(one)
        assertEquals(4, plan(two).places.size)
    }

    @Test
    fun brokenFiles_areRejectedWithAMessage() {
        fun rejects(text: String) {
            val e = runCatching { runBlocking { repo.importTrip(text) } }.exceptionOrNull()
            assertTrue("expected ValidationException for $text, got $e", e is ValidationException)
        }
        rejects("")
        rejects("no es json")
        rejects("{\"format\":\"otra-app\",\"version\":1}")
        rejects("{\"format\":\"tripplanner.trip\",\"version\":99}")
        rejects("{\"format\":\"tripplanner.trip\",\"version\":1}")
        assertEquals(0, runBlocking { repo.observeTripSummaries().first() }.size)
    }

    @Test
    fun shareText_listsTheDaysInOrderWithTimes() {
        val p = plan(sampleTrip())
        val text = p.toShareText()
        assertTrue(text.contains("Día 1"))
        assertTrue(text.indexOf("Livraria Lello") < text.indexOf("Torre dos Clérigos"))
        assertTrue(text.indexOf("Torre dos Clérigos") < text.indexOf("Ribeira"))
        assertTrue(text.contains("10:00 – 11:30"))
        assertTrue(text.contains("Bodega de vino de Oporto"))
        assertTrue(text.contains("Llevar paraguas"))
    }
}
