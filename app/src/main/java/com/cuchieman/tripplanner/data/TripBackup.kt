package com.cuchieman.tripplanner.data

import com.cuchieman.tripplanner.data.local.ChecklistItemEntity
import com.cuchieman.tripplanner.data.local.ExpenseEntity
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.PlaceListEntity
import com.cuchieman.tripplanner.data.local.TripEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One trip as read from a backup file. Ids are the file's own; the importer replaces them. */
data class TripBackupData(
    val trip: TripEntity,
    val lists: List<PlaceListEntity>,
    val places: List<PlaceEntity>,
    val expenses: List<ExpenseEntity>,
    val checklist: List<ChecklistItemEntity> = emptyList(),
)

/**
 * A trip as a small, readable JSON file (fixes.md #5: plans you can always take with you).
 * Dates are ISO strings, times are minutes since midnight, money is minor units.
 * Uses org.json, which ships with Android.
 */
object TripBackup {
    const val FORMAT = "tripplanner.trip"
    const val VERSION = 1

    fun toJson(
        trip: TripEntity,
        lists: List<PlaceListEntity>,
        places: List<PlaceEntity>,
        expenses: List<ExpenseEntity>,
        checklist: List<ChecklistItemEntity> = emptyList(),
    ): String {
        fun day(epochDay: Long?): Any = epochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: JSONObject.NULL
        fun orNull(value: Any?): Any = value ?: JSONObject.NULL

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put(
                "trip", JSONObject()
                    .put("title", trip.title)
                    .put("destination", trip.destinationName)
                    .put("destinationLat", orNull(trip.destinationLat))
                    .put("destinationLng", orNull(trip.destinationLng))
                    .put("start", day(trip.startDate))
                    .put("end", day(trip.endDate))
                    .put("notes", trip.notes)
                    .put("budgetMinor", orNull(trip.budgetMinor))
                    .put("currency", trip.currency)
            )
            .put("lists", JSONArray(lists.map {
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("position", it.position)
                    .put("isDefault", it.isDefault)
            }))
            .put("places", JSONArray(places.map {
                JSONObject()
                    .put("id", it.id)
                    .put("listId", orNull(it.listId))
                    .put("day", day(it.dayDate))
                    .put("position", it.position)
                    .put("name", it.name)
                    .put("address", orNull(it.address))
                    .put("lat", orNull(it.lat))
                    .put("lng", orNull(it.lng))
                    .put("category", orNull(it.category))
                    .put("note", it.note)
                    .put("website", orNull(it.website))
                    .put("phone", orNull(it.phone))
                    .put("startMinute", orNull(it.startMinute))
                    .put("endMinute", orNull(it.endMinute))
            }))
            .put("expenses", JSONArray(expenses.map {
                JSONObject()
                    .put("placeId", orNull(it.placeId))
                    .put("amountMinor", it.amountMinor)
                    .put("currency", it.currency)
                    .put("category", it.category)
                    .put("description", it.description)
                    .put("date", day(it.date))
            }))
            // Optional: files from before the checklist existed simply lack it.
            .put("checklist", JSONArray(checklist.map {
                JSONObject().put("text", it.text).put("done", it.done)
            }))
        return root.toString(2)
    }

    /** Parses a backup file. Anything wrong becomes a [ValidationException] with a message for the user. */
    fun fromJson(text: String): TripBackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw ValidationException("Este archivo no es un viaje de TripPlanner")
        }
        if (root.optString("format") != FORMAT) throw ValidationException("Este archivo no es un viaje de TripPlanner")
        val version = root.optInt("version", -1)
        if (version > VERSION) throw ValidationException("Este viaje viene de una versión más nueva de la app. Actualízala para abrirlo.")
        if (version != VERSION) throw ValidationException("Este archivo no es un viaje de TripPlanner")

        try {
            val t = root.getJSONObject("trip")
            val trip = TripEntity(
                id = "",
                title = t.getString("title"),
                destinationName = t.getString("destination"),
                destinationLat = t.doubleOrNull("destinationLat"),
                destinationLng = t.doubleOrNull("destinationLng"),
                startDate = LocalDate.parse(t.getString("start")).toEpochDay(),
                endDate = LocalDate.parse(t.getString("end")).toEpochDay(),
                coverUri = null,
                notes = t.optString("notes", ""),
                budgetMinor = t.longOrNull("budgetMinor"),
                currency = t.optString("currency", "EUR"),
                createdAt = 0,
                updatedAt = 0,
            )
            val lists = root.optJSONArray("lists").objects().map {
                PlaceListEntity(
                    id = it.getString("id"),
                    tripId = "",
                    title = it.getString("title"),
                    position = it.optInt("position", 0),
                    isDefault = it.optBoolean("isDefault", false),
                    createdAt = 0,
                    updatedAt = 0,
                )
            }
            val places = root.optJSONArray("places").objects().map {
                PlaceEntity(
                    id = it.getString("id"),
                    tripId = "",
                    listId = it.stringOrNull("listId"),
                    dayDate = it.stringOrNull("day")?.let { d -> LocalDate.parse(d).toEpochDay() },
                    position = it.optInt("position", 0),
                    name = it.getString("name"),
                    address = it.stringOrNull("address"),
                    lat = it.doubleOrNull("lat"),
                    lng = it.doubleOrNull("lng"),
                    category = it.stringOrNull("category"),
                    note = it.optString("note", ""),
                    website = it.stringOrNull("website"),
                    phone = it.stringOrNull("phone"),
                    startMinute = it.intOrNull("startMinute"),
                    endMinute = it.intOrNull("endMinute"),
                    createdAt = 0,
                    updatedAt = 0,
                )
            }
            val expenses = root.optJSONArray("expenses").objects().map {
                ExpenseEntity(
                    id = "",
                    tripId = "",
                    placeId = it.stringOrNull("placeId"),
                    amountMinor = it.getLong("amountMinor"),
                    currency = it.optString("currency", trip.currency),
                    category = it.optString("category", "otros"),
                    description = it.optString("description", ""),
                    date = it.stringOrNull("date")?.let { d -> LocalDate.parse(d).toEpochDay() },
                    createdAt = 0,
                    updatedAt = 0,
                )
            }
            val checklist = root.optJSONArray("checklist").objects().mapIndexed { i, it ->
                ChecklistItemEntity(
                    id = "",
                    tripId = "",
                    text = it.getString("text"),
                    done = it.optBoolean("done", false),
                    position = i,
                    createdAt = 0,
                    updatedAt = 0,
                )
            }
            return TripBackupData(trip, lists, places, expenses, checklist)
        } catch (e: JSONException) {
            throw ValidationException("El archivo está dañado o incompleto")
        } catch (e: DateTimeParseException) {
            throw ValidationException("El archivo está dañado o incompleto")
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
}
