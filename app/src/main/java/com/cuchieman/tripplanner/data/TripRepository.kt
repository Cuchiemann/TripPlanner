package com.cuchieman.tripplanner.data

import androidx.room.withTransaction
import com.cuchieman.tripplanner.data.local.ChecklistItemEntity
import com.cuchieman.tripplanner.data.local.ExpenseEntity
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.PlaceListEntity
import com.cuchieman.tripplanner.data.local.TripEntity
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

class ValidationException(message: String) : IllegalArgumentException(message)

/**
 * The app's only data layer. Every rule marked [repo] in replica/schema.sql is checked here,
 * because Room cannot declare CHECK constraints. Multi-row writes run in one transaction.
 */
class TripRepository(private val db: TripPlannerDatabase) {
    private val dao = db.tripDao()

    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()

    // Reading

    fun observeTripSummaries(): Flow<List<TripSummary>> =
        combine(dao.observeTrips(), dao.observePlaceCounts()) { trips, counts ->
            val byTrip = counts.associate { it.tripId to it.count }
            trips.map { TripSummary(it, byTrip[it.id] ?: 0) }
        }

    fun observePlan(tripId: String): Flow<TripPlan?> =
        combine(
            dao.observeTrip(tripId),
            dao.observeLists(tripId),
            dao.observePlaces(tripId),
            dao.observeExpenses(tripId),
            dao.observeChecklist(tripId)
        ) { trip, lists, places, expenses, checklist ->
            trip?.let { TripPlan(it, lists, places, expenses, checklist) }
        }

    suspend fun getExpense(id: String): ExpenseEntity? = dao.getExpense(id)

    suspend fun defaultListId(tripId: String): String =
        dao.getDefaultList(tripId)?.id ?: error("Trip $tripId has no default list")

    // Trips

    suspend fun createTrip(
        title: String,
        destinationName: String,
        lat: Double?,
        lng: Double?,
        start: LocalDate,
        end: LocalDate,
        currency: String = "EUR",
    ): String {
        validateDates(start, end)
        if (destinationName.isBlank()) throw ValidationException("Elige un destino")
        val id = newId()
        val t = now()
        db.withTransaction {
            dao.insertTrip(
                TripEntity(
                    id = id,
                    title = title.trim().ifBlank { "Viaje a ${destinationName.trim()}" },
                    destinationName = destinationName.trim(),
                    destinationLat = lat,
                    destinationLng = lng,
                    startDate = start.toEpochDay(),
                    endDate = end.toEpochDay(),
                    coverUri = null,
                    notes = "",
                    budgetMinor = null,
                    currency = currency,
                    createdAt = t,
                    updatedAt = t
                )
            )
            dao.insertList(
                PlaceListEntity(
                    id = newId(),
                    tripId = id,
                    title = DEFAULT_LIST_TITLE,
                    position = 0,
                    isDefault = true,
                    createdAt = t,
                    updatedAt = t
                )
            )
        }
        return id
    }

    suspend fun renameTrip(tripId: String, title: String) {
        if (title.isBlank()) throw ValidationException("El nombre no puede estar vacío")
        val trip = dao.getTrip(tripId) ?: return
        dao.updateTrip(trip.copy(title = title.trim(), updatedAt = now()))
    }

    suspend fun updateNotes(tripId: String, notes: String) = dao.updateNotes(tripId, notes, now())

    suspend fun deleteTrip(tripId: String) = dao.deleteTrip(tripId)

    /**
     * Moves the trip to new dates. Every place keeps its "day N of the trip". If the trip gets
     * shorter, places on days that no longer exist go to the end of the default list, in order.
     * Never deletes anything. Returns how many places were moved to the list.
     */
    suspend fun changeDates(tripId: String, newStart: LocalDate, newEnd: LocalDate): Int {
        validateDates(newStart, newEnd)
        return db.withTransaction {
            val trip = dao.getTrip(tripId) ?: return@withTransaction 0
            val offset = newStart.toEpochDay() - trip.startDate
            val lastDay = newEnd.toEpochDay()
            val defaultList = dao.getDefaultList(tripId)
                ?: error("Trip $tripId has no default list")
            var nextListPosition = dao.getPlacesInList(defaultList.id).size
            var moved = 0
            val t = now()
            val updated = dao.getDayPlaces(tripId).map { place ->
                val shifted = place.dayDate!! + offset
                if (shifted <= lastDay) {
                    place.copy(dayDate = shifted, updatedAt = t)
                } else {
                    moved++
                    place.copy(
                        dayDate = null,
                        listId = defaultList.id,
                        position = nextListPosition++,
                        startMinute = null,
                        endMinute = null,
                        updatedAt = t
                    )
                }
            }
            dao.updatePlaces(updated)
            dao.updateTrip(
                trip.copy(startDate = newStart.toEpochDay(), endDate = lastDay, updatedAt = t)
            )
            moved
        }
    }

    // Places

    suspend fun addPlace(
        tripId: String,
        target: PlaceTarget,
        name: String,
        address: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        note: String = "",
    ): String {
        if (name.isBlank()) throw ValidationException("Ponle un nombre al lugar")
        if ((lat == null) != (lng == null)) throw ValidationException("Coordenadas incompletas")
        val id = newId()
        val t = now()
        db.withTransaction {
            checkTarget(tripId, target)
            dao.insertPlace(
                PlaceEntity(
                    id = id,
                    tripId = tripId,
                    listId = (target as? PlaceTarget.InList)?.listId,
                    dayDate = (target as? PlaceTarget.Day)?.epochDay,
                    position = siblings(tripId, target).size,
                    name = name.trim(),
                    address = address?.trim()?.ifBlank { null },
                    lat = lat,
                    lng = lng,
                    category = null,
                    note = note,
                    website = null,
                    phone = null,
                    startMinute = null,
                    endMinute = null,
                    createdAt = t,
                    updatedAt = t
                )
            )
        }
        return id
    }

    suspend fun updatePlaceDetails(placeId: String, name: String, note: String, address: String?) {
        if (name.isBlank()) throw ValidationException("Ponle un nombre al lugar")
        val place = dao.getPlace(placeId) ?: return
        dao.updatePlace(
            place.copy(
                name = name.trim(),
                note = note,
                address = address?.trim()?.ifBlank { null },
                updatedAt = now()
            )
        )
    }

    /** fixes.md #7: what a place is (stay, food...). Same keys as expense categories; null clears it. */
    suspend fun setPlaceCategory(placeId: String, category: String?) {
        if (category != null && category !in EXPENSE_CATEGORIES) throw ValidationException("Tipo de lugar desconocido")
        val place = dao.getPlace(placeId) ?: return
        dao.updatePlace(place.copy(category = category, updatedAt = now()))
    }

    suspend fun getPlace(placeId: String): PlaceEntity? = dao.getPlace(placeId)

    /** Sets (or, with nulls, clears) where a place is: for places the search could not find. */
    suspend fun setPlaceLocation(placeId: String, lat: Double?, lng: Double?) {
        if ((lat == null) != (lng == null)) throw ValidationException("Coordenadas incompletas")
        if (lat != null && (lat !in -90.0..90.0 || lng!! !in -180.0..180.0)) {
            throw ValidationException("Esas coordenadas no existen")
        }
        val place = dao.getPlace(placeId) ?: return
        dao.updatePlace(place.copy(lat = lat, lng = lng, updatedAt = now()))
    }

    /** Times are minutes since midnight. Pass both null to clear. */
    suspend fun setPlaceTime(placeId: String, startMinute: Int?, endMinute: Int?) {
        if (endMinute != null && (startMinute == null || endMinute < startMinute)) {
            throw ValidationException("La hora de fin no puede ser antes del inicio")
        }
        val place = dao.getPlace(placeId) ?: return
        dao.updatePlace(place.copy(startMinute = startMinute, endMinute = endMinute, updatedAt = now()))
    }

    /** Moves a place into [target] at [index] (end when null). Renumbers both containers. */
    suspend fun movePlace(placeId: String, target: PlaceTarget, index: Int? = null) {
        db.withTransaction {
            val place = dao.getPlace(placeId) ?: return@withTransaction
            checkTarget(place.tripId, target)
            val t = now()
            val source = place.target

            val oldSiblings = siblings(place.tripId, source).filter { it.id != placeId }
            val newSiblings = if (source == target) oldSiblings else siblings(place.tripId, target)
            val insertAt = (index ?: newSiblings.size).coerceIn(0, newSiblings.size)

            val moved = place.copy(
                listId = (target as? PlaceTarget.InList)?.listId,
                dayDate = (target as? PlaceTarget.Day)?.epochDay,
                // A time belongs to a day; it means nothing in a list.
                startMinute = if (target is PlaceTarget.Day) place.startMinute else null,
                endMinute = if (target is PlaceTarget.Day) place.endMinute else null,
                updatedAt = t
            )
            val reorderedTarget = newSiblings.toMutableList().apply { add(insertAt, moved) }
            val updates = renumber(reorderedTarget, t).toMutableList()
            if (source != target) updates += renumber(oldSiblings, t)
            dao.updatePlaces(updates)
        }
    }

    /** One step up (-1) or down (+1) inside its own day or list. The accessible alternative to dragging. */
    suspend fun nudgePlace(placeId: String, delta: Int) {
        val place = dao.getPlace(placeId) ?: return
        movePlace(placeId, place.target, (place.position + delta).coerceAtLeast(0))
    }

    /** Deletes and returns the row so the caller can offer undo. */
    suspend fun deletePlace(placeId: String): PlaceEntity? = db.withTransaction {
        val place = dao.getPlace(placeId) ?: return@withTransaction null
        dao.deletePlace(placeId)
        dao.updatePlaces(renumber(siblings(place.tripId, place.target), now()))
        place
    }

    /** Undo for [deletePlace]. Linked expenses stay unlinked (their place_id was set to null). */
    suspend fun restorePlace(place: PlaceEntity) {
        db.withTransaction {
            val siblings = siblings(place.tripId, place.target).toMutableList()
            val at = place.position.coerceIn(0, siblings.size)
            dao.insertPlace(place.copy(position = siblings.size))
            siblings.add(at, place)
            dao.updatePlaces(renumber(siblings, now()))
        }
    }

    // Budget

    suspend fun setBudget(tripId: String, budgetMinor: Long?) {
        if (budgetMinor != null && budgetMinor <= 0) throw ValidationException("Introduce un importe mayor que 0")
        val trip = dao.getTrip(tripId) ?: return
        dao.updateTrip(trip.copy(budgetMinor = budgetMinor, updatedAt = now()))
    }

    /** Insert when [id] is null, update otherwise. */
    suspend fun saveExpense(
        id: String?,
        tripId: String,
        amountMinor: Long,
        category: String,
        description: String,
        placeId: String?,
        date: LocalDate?,
    ): String {
        if (amountMinor <= 0) throw ValidationException("Introduce un importe mayor que 0")
        if (category !in EXPENSE_CATEGORIES) throw ValidationException("Categoría desconocida")
        val trip = dao.getTrip(tripId) ?: throw ValidationException("El viaje ya no existe")
        val t = now()
        val existing = id?.let { dao.getExpense(it) }
        val row = ExpenseEntity(
            id = existing?.id ?: newId(),
            tripId = tripId,
            placeId = placeId,
            amountMinor = amountMinor,
            currency = trip.currency,
            category = category,
            description = description.trim(),
            date = date?.toEpochDay(),
            createdAt = existing?.createdAt ?: t,
            updatedAt = t
        )
        if (existing == null) dao.insertExpense(row) else dao.updateExpense(row)
        return row.id
    }

    suspend fun deleteExpense(id: String) = dao.deleteExpense(id)

    // Lists on the overview (could-have: custom extra lists)

    suspend fun createList(tripId: String, title: String): String {
        if (title.isBlank()) throw ValidationException("Ponle un nombre a la lista")
        val t = now()
        val id = newId()
        db.withTransaction {
            dao.insertList(PlaceListEntity(id, tripId, title.trim(), dao.countLists(tripId), false, t, t))
        }
        return id
    }

    suspend fun renameList(listId: String, title: String) {
        if (title.isBlank()) throw ValidationException("Ponle un nombre a la lista")
        val list = dao.getList(listId) ?: return
        dao.updateList(list.copy(title = title.trim(), updatedAt = now()))
    }

    /** Deletes a list and the places in it. The default list cannot be deleted. */
    suspend fun deleteList(listId: String) {
        val list = dao.getList(listId) ?: return
        if (list.isDefault) throw ValidationException("La lista principal no se puede borrar")
        dao.deleteList(listId)
    }

    // Several places at once (could-have: bulk move and delete)

    /** Moves the places to the end of [target], keeping the order they had among themselves. */
    suspend fun movePlaces(placeIds: List<String>, target: PlaceTarget) {
        db.withTransaction {
            val ordered = dao.getPlaces(placeIds).sortedWith(
                compareBy<PlaceEntity>({ it.dayDate ?: Long.MAX_VALUE }, { it.listId ?: "" }, { it.position })
            )
            ordered.forEach { movePlace(it.id, target) }
        }
    }

    /** Deletes the places; pass the result to [restorePlaces] to undo. */
    suspend fun deletePlaces(placeIds: List<String>): List<PlaceEntity> =
        db.withTransaction { placeIds.mapNotNull { deletePlace(it) } }

    suspend fun restorePlaces(deleted: List<PlaceEntity>) {
        // Undo in reverse order, so every place goes back to the position it was taken from.
        db.withTransaction { deleted.asReversed().forEach { restorePlace(it) } }
    }

    // Checklist (could-have: packing list), schema v2

    suspend fun addChecklistItem(tripId: String, text: String): String {
        if (text.isBlank()) throw ValidationException("Escribe algo para la lista")
        val t = now()
        val id = newId()
        db.withTransaction {
            dao.insertChecklistItem(ChecklistItemEntity(id, tripId, text.trim(), false, dao.countChecklist(tripId), t, t))
        }
        return id
    }

    suspend fun setChecklistDone(itemId: String, done: Boolean) {
        val item = dao.getChecklistItem(itemId) ?: return
        dao.updateChecklistItem(item.copy(done = done, updatedAt = now()))
    }

    suspend fun deleteChecklistItem(itemId: String): ChecklistItemEntity? {
        val item = dao.getChecklistItem(itemId) ?: return null
        dao.deleteChecklistItem(itemId)
        return item
    }

    suspend fun restoreChecklistItem(item: ChecklistItemEntity) = dao.insertChecklistItem(item)

    // Cover photo (should-have), stored as a file path in the app's own storage

    suspend fun setCover(tripId: String, path: String?) {
        val trip = dao.getTrip(tripId) ?: return
        dao.updateTrip(trip.copy(coverUri = path, updatedAt = now()))
    }

    /**
     * Deletes a trip but returns what undo needs: its backup JSON and cover path.
     * Undo is [importTrip] + [setCover]; the trip comes back with new ids.
     */
    suspend fun deleteTripForUndo(tripId: String): DeletedTrip? {
        val trip = dao.getTrip(tripId) ?: return null
        val json = exportTrip(tripId)
        dao.deleteTrip(tripId)
        return DeletedTrip(trip.title, json, trip.coverUri)
    }

    suspend fun undoDeleteTrip(deleted: DeletedTrip): String {
        val id = importTrip(deleted.backupJson)
        if (deleted.coverPath != null) setCover(id, deleted.coverPath)
        return id
    }

    // Backup (fixes.md #5)

    /** The whole trip as a JSON backup file's text. */
    suspend fun exportTrip(tripId: String): String {
        val trip = dao.getTrip(tripId) ?: throw ValidationException("El viaje ya no existe")
        return TripBackup.toJson(
            trip,
            dao.observeLists(tripId).first(),
            dao.observePlaces(tripId).first(),
            dao.observeExpenses(tripId).first(),
            dao.observeChecklist(tripId).first()
        )
    }

    /**
     * Adds the trip in [json] as a new trip with fresh ids, so importing the same file twice
     * gives two independent trips. Repairs what it can (missing default list, places outside
     * the dates) instead of refusing the file. Returns the new trip id.
     */
    suspend fun importTrip(json: String): String {
        val backup = TripBackup.fromJson(json)
        val start = LocalDate.ofEpochDay(backup.trip.startDate)
        val end = LocalDate.ofEpochDay(backup.trip.endDate)
        validateDates(start, end)
        if (backup.trip.destinationName.isBlank()) throw ValidationException("El archivo está dañado o incompleto")

        val t = now()
        val tripId = newId()
        val listIds = backup.lists.associate { it.id to newId() }
        val placeIds = backup.places.associate { it.id to newId() }

        var lists = backup.lists.map { it.copy(id = listIds.getValue(it.id), tripId = tripId, createdAt = t, updatedAt = t) }
        if (lists.none { it.isDefault }) {
            lists = lists + PlaceListEntity(newId(), tripId, DEFAULT_LIST_TITLE, lists.size, true, t, t)
        }
        val defaultList = lists.first { it.isDefault }.id

        // Each place goes back into its day or list; anything that no longer fits goes to the default list.
        val placed = backup.places.map { p ->
            val listId = p.listId?.let { listIds[it] }
            val day = p.dayDate?.takeIf { it in backup.trip.startDate..backup.trip.endDate }
            val target: PlaceTarget = when {
                day != null && listId == null -> PlaceTarget.Day(day)
                listId != null && day == null -> PlaceTarget.InList(listId)
                else -> PlaceTarget.InList(defaultList)
            }
            target to p.copy(
                id = placeIds.getValue(p.id),
                tripId = tripId,
                listId = (target as? PlaceTarget.InList)?.listId,
                dayDate = (target as? PlaceTarget.Day)?.epochDay,
                startMinute = if (target is PlaceTarget.Day) p.startMinute else null,
                endMinute = if (target is PlaceTarget.Day) p.endMinute else null,
                category = p.category?.takeIf { it in EXPENSE_CATEGORIES },
                createdAt = t,
                updatedAt = t
            )
        }
        val places = placed.groupBy({ it.first }, { it.second })
            .flatMap { (_, inContainer) -> renumber(inContainer.sortedBy { it.position }, t) }

        val expenses = backup.expenses
            .filter { it.amountMinor > 0 }
            .map {
                it.copy(
                    id = newId(),
                    tripId = tripId,
                    placeId = it.placeId?.let { old -> placeIds[old] },
                    currency = backup.trip.currency,
                    category = it.category.takeIf { c -> c in EXPENSE_CATEGORIES } ?: "otros",
                    createdAt = t,
                    updatedAt = t
                )
            }

        db.withTransaction {
            dao.insertTrip(backup.trip.copy(id = tripId, createdAt = t, updatedAt = t))
            lists.forEach { dao.insertList(it) }
            places.forEach { dao.insertPlace(it) }
            expenses.forEach { dao.insertExpense(it) }
            backup.checklist.filter { it.text.isNotBlank() }.forEachIndexed { i, item ->
                dao.insertChecklistItem(item.copy(id = newId(), tripId = tripId, position = i, createdAt = t, updatedAt = t))
            }
        }
        return tripId
    }

    // Helpers

    private suspend fun siblings(tripId: String, target: PlaceTarget): List<PlaceEntity> = when (target) {
        is PlaceTarget.Day -> dao.getPlacesInDay(tripId, target.epochDay)
        is PlaceTarget.InList -> dao.getPlacesInList(target.listId)
    }

    private suspend fun checkTarget(tripId: String, target: PlaceTarget) {
        if (target is PlaceTarget.Day) {
            val trip = dao.getTrip(tripId) ?: throw ValidationException("El viaje ya no existe")
            if (target.epochDay !in trip.startDate..trip.endDate) {
                throw ValidationException("Ese día no está dentro del viaje")
            }
        }
    }

    /** Gapless 0-based positions in list order. Returns every row, ready for one batch update. */
    private fun renumber(places: List<PlaceEntity>, t: Long): List<PlaceEntity> =
        places.mapIndexed { i, p -> if (p.position == i) p else p.copy(position = i, updatedAt = t) }

    private fun validateDates(start: LocalDate, end: LocalDate) {
        if (end.isBefore(start)) throw ValidationException("La vuelta no puede ser antes de la ida")
        if (end.toEpochDay() - start.toEpochDay() > MAX_DAYS - 1) {
            throw ValidationException("Como máximo $MAX_DAYS días")
        }
    }

    companion object {
        const val DEFAULT_LIST_TITLE = "Lugares por visitar"
        const val MAX_DAYS = 90
        val EXPENSE_CATEGORIES = setOf("transporte", "alojamiento", "comida", "actividades", "compras", "otros")
    }
}
