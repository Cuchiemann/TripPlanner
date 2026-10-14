package com.cuchieman.tripplanner.ui

import android.content.Context
import android.content.Intent
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.local.PlaceEntity

/**
 * The itinerary as plain text, to paste in a chat or an email (fixes.md #5): something to
 * share with people who don't have the app, without giving them the trip itself.
 */
fun TripPlan.toShareText(): String = buildString {
    appendLine(trip.title)
    appendLine("${formatRange(start, end)} · ${trip.destinationName}")

    days.forEachIndexed { i, day ->
        appendLine()
        appendLine("Día ${i + 1} · ${formatDayTitle(day)}")
        val places = placesOn(day)
        if (places.isEmpty()) appendLine("  (día libre)")
        places.forEach { appendPlace(it) }
    }

    lists.forEach { list ->
        val places = placesIn(list.id)
        if (places.isNotEmpty()) {
            appendLine()
            appendLine(list.title)
            places.forEach { appendPlace(it) }
        }
    }

    if (trip.notes.isNotBlank()) {
        appendLine()
        appendLine("Notas")
        appendLine(trip.notes.trim())
    }
}.trimEnd()

private fun StringBuilder.appendPlace(place: PlaceEntity) {
    val time = formatTimeRange(place.startMinute, place.endMinute)
    appendLine(if (time != null) "  $time  ${place.name}" else "  · ${place.name}")
    place.address?.let { appendLine("      $it") }
    if (place.note.isNotBlank()) appendLine("      ${place.note.trim().replace("\n", "\n      ")}")
}

fun shareTripText(context: Context, plan: TripPlan) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, plan.trip.title)
        .putExtra(Intent.EXTRA_TEXT, plan.toShareText())
    context.startActivity(Intent.createChooser(send, "Compartir itinerario"))
}
