package com.cuchieman.tripplanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.hasCoordinates
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The UI is written in Spanish, so dates and money are formatted for es-ES.
val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", SpanishLocale)
private val chipFormat = DateTimeFormatter.ofPattern("EEE d/M", SpanishLocale)
private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishLocale)

/** "3 may – 7 may · 5 días" */
fun formatRange(start: LocalDate, end: LocalDate, withLength: Boolean = true): String {
    val days = end.toEpochDay() - start.toEpochDay() + 1
    val range = "${start.format(dayMonth)} – ${end.format(dayMonth)}"
    return if (withLength) "$range · $days ${if (days == 1L) "día" else "días"}" else range
}

/** "sáb 3/5" */
fun formatDayChip(date: LocalDate): String = date.format(chipFormat)

/** "Sábado 3 de mayo" */
fun formatDayTitle(date: LocalDate): String =
    date.format(dayTitleFormat).replaceFirstChar { it.titlecase(SpanishLocale) }

/** 570 -> "9:30" */
fun formatMinute(minute: Int): String = "%d:%02d".format(minute / 60, minute % 60)

fun formatTimeRange(start: Int?, end: Int?): String? = when {
    start == null -> null
    end == null -> formatMinute(start)
    else -> "${formatMinute(start)} – ${formatMinute(end)}"
}

/** The Material date picker hands back UTC midnight. */
fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun dateToUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun formatMoney(minor: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val format = NumberFormat.getCurrencyInstance(SpanishLocale).apply { this.currency = currency }
    return format.format(BigDecimal.valueOf(minor, currency.defaultFractionDigits))
}

fun currencySymbol(currencyCode: String): String = Currency.getInstance(currencyCode).getSymbol(SpanishLocale)

/** "12,50" or "12.5" -> 1250. Null when it is not a valid positive amount. */
fun parseMoney(text: String, currencyCode: String): Long? {
    val digits = Currency.getInstance(currencyCode).defaultFractionDigits
    return text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }
        ?.movePointRight(digits)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()
}

/** 1250 -> "12,50" for editing. */
fun minorToInput(minor: Long, currencyCode: String): String {
    val digits = Currency.getInstance(currencyCode).defaultFractionDigits
    return BigDecimal.valueOf(minor, digits).stripTrailingZeros().toPlainString().replace('.', ',')
}

/** Great-circle distance in km. The MVP shows straight-line distance, not travel time. */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun formatKm(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()} m" else String.format(SpanishLocale, "%.1f km", km)

/**
 * fixes.md #8: straight-line km along a day's stops in order, skipping stops without a
 * location. Null when fewer than two stops have one.
 */
fun dayDistanceKm(places: List<PlaceEntity>): Double? {
    val located = places.filter { it.hasCoordinates }
    if (located.size < 2) return null
    return located.zipWithNext { a, b -> haversineKm(a.lat!!, a.lng!!, b.lat!!, b.lng!!) }.sum()
}

fun distanceText(from: PlaceEntity, to: PlaceEntity): String? {
    if (!from.hasCoordinates || !to.hasCoordinates) return null
    return formatKm(haversineKm(from.lat!!, from.lng!!, to.lat!!, to.lng!!))
}

/** Google Maps directions through its public URL scheme; any maps app or the browser can take it. */
fun openDirections(context: Context, place: PlaceEntity) {
    val destination = if (place.hasCoordinates) "${place.lat},${place.lng}" else place.address ?: place.name
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination))
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
