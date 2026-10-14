package com.cuchieman.tripplanner

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cuchieman.tripplanner.data.CoverStore
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import com.cuchieman.tripplanner.ui.openDayRoute
import com.cuchieman.tripplanner.ui.openDirections
import com.cuchieman.tripplanner.ui.toHtml
import com.cuchieman.tripplanner.ui.toKml
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/** Second test pass: the edge cases the first one left as "todo", and the newer features'. */
@RunWith(AndroidJUnit4::class)
class EdgeCasesTest {
    private val context get() = ApplicationProvider.getApplicationContext<TripPlannerApplication>()
    private lateinit var db: TripPlannerDatabase
    private lateinit var repo: TripRepository
    private val start = LocalDate.of(2026, 5, 3)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TripPlannerDatabase::class.java).build()
        repo = TripRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun plan(id: String): TripPlan = runBlocking { repo.observePlan(id).first()!! }
    private fun day(n: Int) = PlaceTarget.Day(start.plusDays(n.toLong() - 1).toEpochDay())

    private val nasty = "Bar \"Tom & Jerry\" <café> ☕ ñ"

    // KML and print HTML must stay well-formed whatever the user types.

    @Test
    fun kml_isValidXml_andKeepsNamesWithSymbols() {
        val id = runBlocking {
            val id = repo.createTrip(nasty, "Lisboa", null, null, start, start.plusDays(1))
            repo.addPlace(id, day(1), nasty, "Rua <1> & 2", 38.7, -9.1, "nota \"rara\" & <b>")
            repo.addPlace(id, day(1), "Sin ubicación")
            id
        }
        val kml = plan(id).toKml()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(kml.byteInputStream())
        val names = doc.getElementsByTagName("Placemark").let { list ->
            (0 until list.length).map { (list.item(it) as Element).getElementsByTagName("name").item(0).textContent }
        }
        assertEquals(listOf("1. $nasty"), names) // the place without a location is left out
    }

    @Test
    fun printHtml_escapesWhatTheUserTyped() {
        val id = runBlocking {
            val id = repo.createTrip("<script>alert(1)</script>", "Lisboa", null, null, start, start)
            repo.addPlace(id, day(1), nasty)
            id
        }
        val html = plan(id).toHtml()
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("Tom &amp; Jerry"))
    }

    // Backup files from before the packing list existed still import.

    @Test
    fun backupWithoutChecklist_fromTheFirstVersion_imports() = runBlocking {
        val json = """
            {"format":"tripplanner.trip","version":1,
             "trip":{"title":"Viejo","destination":"Roma","destinationLat":null,"destinationLng":null,
                     "start":"2026-05-03","end":"2026-05-04","notes":"","budgetMinor":null,"currency":"EUR"},
             "lists":[{"id":"l","title":"Lugares por visitar","position":0,"isDefault":true}],
             "places":[{"id":"p","listId":null,"day":"2026-05-04","position":0,"name":"Coliseo","address":null,
                        "lat":41.89,"lng":12.49,"category":"casino","note":"","website":null,"phone":null,
                        "startMinute":null,"endMinute":null}],
             "expenses":[]}
        """.trimIndent()
        val id = repo.importTrip(json)
        val p = plan(id)
        assertEquals(listOf("Coliseo"), p.placesOn(start.plusDays(1)).map { it.name })
        assertNull(p.places.single().category) // unknown type dropped, not crashing
        assertTrue(p.checklist.isEmpty())
    }

    // Deleting a list keeps the money spent at its places.

    @Test
    fun deletingAListWithPaidPlaces_keepsTheExpenses() = runBlocking {
        val id = repo.createTrip("x", "Lisboa", null, null, start, start)
        val list = repo.createList(id, "Museos")
        val museum = repo.addPlace(id, PlaceTarget.InList(list), "Gulbenkian")
        repo.saveExpense(null, id, 1_400, "actividades", "Entrada", museum, null)
        repo.deleteList(list)
        val expense = plan(id).expenses.single()
        assertEquals(1_400L, expense.amountMinor)
        assertNull(expense.placeId)
    }

    // Cover photos: scaled down, replaced cleanly, never deleting files outside their folder.

    @Test
    fun coverStore_scalesDown_andOnlyDeletesItsOwnFiles() = runBlocking {
        val big = File(context.cacheDir, "big.png")
        big.outputStream().use { Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val covers = CoverStore(context)
        val path = covers.save("t1", Uri.fromFile(big))
        val saved = android.graphics.BitmapFactory.decodeFile(path)
        assertTrue("scaled to ${saved.width}x${saved.height}", saved.width <= 2000 && saved.height <= 2000)

        covers.delete(big.absolutePath) // not in the covers folder: must be left alone
        assertTrue(big.exists())
        covers.delete(path)
        assertFalse(File(path).exists())
        big.delete()
        Unit
    }

    // Directions and day routes open Google Maps' public URL with the right coordinates.

    @Test
    fun directions_andDayRoute_openTheRightUrl() {
        val id = runBlocking {
            val id = repo.createTrip("x", "Lisboa", null, null, start, start)
            repo.addPlace(id, day(1), "Torre", null, 38.6916, -9.2160)
            repo.addPlace(id, day(1), "Sin ubicación")
            repo.addPlace(id, day(1), "Monasterio", null, 38.6979, -9.2065)
            repo.addPlace(id, day(1), "Castillo", null, 38.7139, -9.1335)
            id
        }
        val places = plan(id).placesOn(start)
        Intents.init()
        // Record the intents but do not actually open a browser.
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(0, null))
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                openDirections(context, places.first())
                assertTrue(openDayRoute(context, places))
                assertFalse(openDayRoute(context, places.take(2))) // only one located stop
            }
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode("38.6916,-9.216")))))
            intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(
                        allOf(
                            hasToString(containsString("origin=" + Uri.encode("38.6916,-9.216"))),
                            hasToString(containsString("destination=" + Uri.encode("38.7139,-9.1335"))),
                            hasToString(containsString("waypoints=" + Uri.encode("38.6979,-9.2065")))
                        )
                    )
                )
            )
        } finally {
            Intents.release()
        }
    }

    private fun hasToString(matcher: org.hamcrest.Matcher<String>): org.hamcrest.Matcher<Uri> =
        org.hamcrest.Matchers.hasToString<Uri>(matcher)
}
