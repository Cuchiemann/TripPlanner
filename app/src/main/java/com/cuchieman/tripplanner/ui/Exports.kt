package com.cuchieman.tripplanner.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.hasCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Ways out of the app: Google Maps, Google My Maps (KML), paper or PDF.

/** Google Maps allows at most 9 stops between origin and destination in a directions link. */
const val MAPS_MAX_WAYPOINTS = 9

/**
 * Opens a day's route in Google Maps through its public directions URL: first stop as the
 * origin, last as the destination, the rest as waypoints. Stops without a location are skipped.
 * Returns false when there are fewer than two located stops.
 */
fun openDayRoute(context: Context, places: List<PlaceEntity>): Boolean {
    val stops = places.filter { it.hasCoordinates }
    if (stops.size < 2) return false
    fun ll(p: PlaceEntity) = "${p.lat},${p.lng}"
    val middle = stops.subList(1, stops.size - 1).take(MAPS_MAX_WAYPOINTS)
    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=").append(Uri.encode(ll(stops.first())))
        append("&destination=").append(Uri.encode(ll(stops.last())))
        if (middle.isNotEmpty()) append("&waypoints=").append(Uri.encode(middle.joinToString("|") { ll(it) }))
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    return true
}

private fun String.xml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** KML with one folder per day plus one per list; places without a location are left out. */
fun TripPlan.toKml(): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""")
    appendLine("<name>${trip.title.xml()}</name>")
    fun folder(name: String, places: List<PlaceEntity>) {
        val located = places.filter { it.hasCoordinates }
        if (located.isEmpty()) return
        appendLine("<Folder><name>${name.xml()}</name>")
        located.forEachIndexed { i, p ->
            val desc = listOfNotNull(
                formatTimeRange(p.startMinute, p.endMinute),
                p.address,
                p.note.takeIf { it.isNotBlank() }
            ).joinToString("\n")
            appendLine("<Placemark><name>${(i + 1)}. ${p.name.xml()}</name>")
            if (desc.isNotEmpty()) appendLine("<description>${desc.xml()}</description>")
            appendLine("<Point><coordinates>${p.lng},${p.lat}</coordinates></Point></Placemark>")
        }
        appendLine("</Folder>")
    }
    days.forEachIndexed { i, d -> folder("Día ${i + 1} · ${formatDayTitle(d)}", placesOn(d)) }
    lists.forEach { folder(it.title, placesIn(it.id)) }
    appendLine("</Document></kml>")
}

/** The itinerary as a simple printable page. */
fun TripPlan.toHtml(): String = buildString {
    fun h(s: String) = s.xml().replace("\n", "<br>")
    append("<html><head><meta charset='utf-8'><style>")
    append("body{font-family:sans-serif;color:#1b1f24;margin:24px} h1{margin:0} .meta{color:#5c636a;margin:4px 0 16px}")
    append("h2{font-size:16px;margin:20px 0 6px;border-bottom:1px solid #dee2e6;padding-bottom:4px}")
    append(".p{margin:4px 0 8px} .t{display:inline-block;width:110px;color:#1a4f9e} .n{color:#5c636a;margin-left:110px;font-size:13px}")
    append("</style></head><body>")
    append("<h1>${h(trip.title)}</h1><div class='meta'>${h(formatRange(start, end))} · ${h(trip.destinationName)}</div>")
    fun place(p: PlaceEntity) {
        val time = formatTimeRange(p.startMinute, p.endMinute) ?: ""
        append("<div class='p'><span class='t'>${h(time)}</span><b>${h(p.name)}</b>")
        p.address?.let { append("<div class='n'>${h(it)}</div>") }
        if (p.note.isNotBlank()) append("<div class='n'>${h(p.note.trim())}</div>")
        append("</div>")
    }
    days.forEachIndexed { i, d ->
        append("<h2>Día ${i + 1} · ${h(formatDayTitle(d))}</h2>")
        val ps = placesOn(d)
        if (ps.isEmpty()) append("<div class='n' style='margin-left:0'>Día libre</div>") else ps.forEach(::place)
    }
    lists.forEach { l ->
        val ps = placesIn(l.id)
        if (ps.isNotEmpty()) {
            append("<h2>${h(l.title)}</h2>")
            ps.forEach(::place)
        }
    }
    if (checklist.isNotEmpty()) {
        append("<h2>Lista de equipaje</h2>")
        checklist.forEach { append("<div class='p'>${if (it.done) "☑" else "☐"} ${h(it.text)}</div>") }
    }
    if (trip.notes.isNotBlank()) append("<h2>Notas</h2><div class='p'>${h(trip.notes.trim())}</div>")
    append("</body></html>")
}

// Keeps the WebView alive until printing has started; the print framework needs it.
private var printingWebView: WebView? = null

/** Android's print dialog, which also offers "Guardar como PDF" (could-have: print the trip). */
fun printTrip(context: Context, plan: TripPlan) {
    val webView = WebView(context)
    printingWebView = webView
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val name = plan.trip.title
            manager.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
            printingWebView = null
        }
    }
    webView.loadDataWithBaseURL(null, plan.toHtml(), "text/html", "UTF-8", null)
}

/** The trip's cover photo, decoded off the main thread; null while loading or without one. */
@Composable
fun rememberCoverBitmap(path: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path) {
    value = if (path == null) null else withContext(Dispatchers.IO) {
        runCatching {
            if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
        }.getOrNull()
    }
}
