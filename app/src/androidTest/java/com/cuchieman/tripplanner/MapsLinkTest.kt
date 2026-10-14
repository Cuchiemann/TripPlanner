package com.cuchieman.tripplanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cuchieman.tripplanner.data.MapsLink
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.ValidationException
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Places the geocoder cannot find (a hermitage, a nature reserve's visitor centre) can be
 * added from a Google Maps link or plain coordinates. The URLs below are the real final URLs
 * the two short links in the user's Santa Pola document lead to.
 */
@RunWith(AndroidJUnit4::class)
class MapsLinkTest {
    private val ermita = "https://www.google.com/maps/place/Ermita+de+San+Francisco+de+As%C3%ADs/@38.15328,-0.6903343,777m/data=!3m2!1e3!4b1!4m6!3m5!1s0xd63b1e435feb459:0x454249dbcf07c656!8m2!3d38.15328!4d-0.6903343!16s%2Fg%2F11xkr7c3s?entry=tts"
    private val hondo = "https://www.google.com/maps/place/Centro+de+Interpretación+Parque+Natural+El+Hondo/@38.185961,-0.7895779,776m/data=!3m1!1e3!4m6!3m5!1s0xd63bb04b15f6647:0xf72081b49f01b1c1!8m2!3d38.1868971!4d-0.7899212!16s%2Fg%2F11xtmly3s"

    @Test
    fun placeUrl_givesNameAndThePinNotTheViewport() {
        val r = MapsLink.parse(hondo)!!
        assertEquals("Centro de Interpretación Parque Natural El Hondo", r.name)
        // !3d/!4d is the pin; @ is only where the map was centred.
        assertEquals(38.1868971, r.lat, 1e-9)
        assertEquals(-0.7899212, r.lng, 1e-9)

        val e = MapsLink.parse(ermita)!!
        assertEquals("Ermita de San Francisco de Asís", e.name)
        assertEquals(38.15328, e.lat, 1e-9)
        assertEquals(-0.6903343, e.lng, 1e-9)
    }

    @Test
    fun otherLinkShapes() {
        val at = MapsLink.parse("https://www.google.com/maps/@38.19,-0.76,15z")!!
        assertEquals(38.19, at.lat, 1e-9)
        assertEquals(-0.76, at.lng, 1e-9)
        val q = MapsLink.parse("https://maps.google.com/?q=38.153280,-0.690334")!!
        assertEquals(38.15328, q.lat, 1e-6)
        // In the EU the short link may land on Google's consent page, with the real URL inside.
        val consent = "https://consent.google.com/m?continue=" + java.net.URLEncoder.encode(ermita, "UTF-8") + "&gl=ES"
        assertEquals("Ermita de San Francisco de Asís", MapsLink.parse(consent)!!.name)
        assertNull(MapsLink.parse("https://www.google.com/search?q=ermita"))
    }

    @Test
    fun plainCoordinates() {
        for (text in listOf("38.15328, -0.6903343", "38.15328,-0.6903343", " 38.15328 , -0.6903343 ", "38.15328 -0.6903343")) {
            val c = MapsLink.coordinates(text)!!
            assertEquals(text, 38.15328, c.first, 1e-9)
            assertEquals(text, -0.6903343, c.second, 1e-9)
        }
        assertNull(MapsLink.coordinates("Ermita de Sant Francesc"))
        assertNull(MapsLink.coordinates("95.0, 10.0")) // latitude out of range
        assertNull(MapsLink.coordinates("1, 2")) // whole numbers are more likely a typo than a place
    }

    @Test
    fun recognisesWhatToResolve() {
        assertTrue(MapsLink.isLink("https://maps.app.goo.gl/sZkxLQm18DMRYUeq9"))
        assertTrue(MapsLink.isLink("https://goo.gl/maps/abc"))
        assertTrue(MapsLink.isLink(hondo))
        assertFalse(MapsLink.isLink("El Hondo"))
        assertFalse(MapsLink.isLink("https://example.com/maps"))
    }

    // What the Google Maps app's "Compartir" puts on the clipboard is not just the link.

    @Test
    fun sharedFromTheMapsApp_theLinkIsFoundInsideTheText() {
        val android = "Ermita de San Francisco de Asís\nhttps://maps.app.goo.gl/sZkxLQm18DMRYUeq9?g_st=aw"
        assertEquals("https://maps.app.goo.gl/sZkxLQm18DMRYUeq9?g_st=aw", MapsLink.findLink(android))
        assertEquals("Ermita de San Francisco de Asís", MapsLink.labelBefore(android))

        val withAddress = "Centro de Interpretación Parque Natural El Hondo\nFinca El Rincón, 03330 Crevillent, Alicante\nhttps://maps.app.goo.gl/o6DFz9z58Cvptp5v8?g_st=ac"
        assertEquals("https://maps.app.goo.gl/o6DFz9z58Cvptp5v8?g_st=ac", MapsLink.findLink(withAddress))
        assertEquals("Centro de Interpretación Parque Natural El Hondo", MapsLink.labelBefore(withAddress))

        // Pasted into a one-line field the line breaks may become spaces.
        val oneLine = "Ermita de San Francisco de Asís https://maps.app.goo.gl/sZkxLQm18DMRYUeq9?g_st=aw"
        assertEquals("https://maps.app.goo.gl/sZkxLQm18DMRYUeq9?g_st=aw", MapsLink.findLink(oneLine))

        assertNull(MapsLink.findLink("El Hondo, Crevillent"))
        assertNull(MapsLink.findLink("mira https://example.com/maps"))
    }

    @Test
    fun sharedDroppedPin_coordinatesInsideText() {
        val c = MapsLink.coordinatesIn("Ubicación marcada 38.153280, -0.690334 cerca de Elche")!!
        assertEquals(38.15328, c.first, 1e-6)
        assertEquals(-0.690334, c.second, 1e-6)
        assertNull(MapsLink.coordinatesIn("Salida 7.00, regreso 22.45")) // times are not coordinates
    }

    // Newer shares lead to /maps/place/Name/data=!4m2!3m1!1s0x…:0x… with no coordinates at all.

    @Test
    fun placeUrlWithoutCoordinates_pinComesFromThePlaceDetails() {
        val url = "https://www.google.com/maps/place/Patraix,+Valencia/data=!4m2!3m1!1s0xd604f3e4c0a9675:0x70ce4962f896a560!18m1!1e1?entry=gps&g_st=ac"
        assertNull(MapsLink.parse(url))
        assertEquals("Patraix, Valencia", MapsLink.nameIn(url))
        // Trimmed from the real response: the map's centre first, then the place with its pin before its id.
        val details = """)]}'
            [null,null,null,null,[[6160.6,-0.3921692,39.46220615],[0,0,0]],null,["x","y",["Valencia"],null,null,
            [null,null,39.1,-0.1],"0xaaaa:0xbbbb","Otro",null,[null,null,39.462494,-0.3913172],"0xd604f3e4c0a9675:0x70ce4962f896a560","Patraix"]"""
        val pin = MapsLink.pinInDetails(details, "0xd604f3e4c0a9675:0x70ce4962f896a560")!!
        assertEquals(39.462494, pin.first, 1e-9)
        assertEquals(-0.3913172, pin.second, 1e-9)
        assertNull(MapsLink.pinInDetails("<html>nothing here</html>", null))
    }

    /** The real link from the bug report, followed over the network. Skipped offline. */
    @Test
    fun liveLink_sharedFromTheMapsApp() = runBlocking {
        val online = runCatching { java.net.InetAddress.getByName("maps.app.goo.gl") }.isSuccess
        org.junit.Assume.assumeTrue("no network", online)
        val r = MapsLink.fromText("Patraix\nhttps://maps.app.goo.gl/JZZ4SSKJPuvt9MjZ6?g_st=ac")!!
        assertEquals(39.4625, r.lat, 0.01)
        assertEquals(-0.3913, r.lng, 0.01)
        assertEquals("Patraix, Valencia", r.name)
    }

    @Test
    fun repository_setsAndClearsALocation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TripPlannerDatabase::class.java
        ).build()
        try {
            val repo = TripRepository(db)
            val start = LocalDate.of(2026, 6, 25)
            val id = repo.createTrip("Aves", "Santa Pola", null, null, start, start)
            val p = repo.addPlace(id, PlaceTarget.Day(start.toEpochDay()), "Ermita de Sant Francesc")
            repo.setPlaceLocation(p, 38.15328, -0.6903343)
            val placed = repo.observePlan(id).first()!!.place(p)!!
            assertEquals(38.15328, placed.lat!!, 1e-9)
            val failed = runCatching { repo.setPlaceLocation(p, 120.0, 0.0) }.exceptionOrNull()
            assertTrue(failed is ValidationException)
            repo.setPlaceLocation(p, null, null)
            assertNull(repo.observePlan(id).first()!!.place(p)!!.lat)
        } finally {
            db.close()
        }
    }
}
