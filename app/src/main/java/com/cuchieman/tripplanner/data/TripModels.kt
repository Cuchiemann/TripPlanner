package com.cuchieman.tripplanner.data

import com.cuchieman.tripplanner.data.local.ChecklistItemEntity
import com.cuchieman.tripplanner.data.local.ExpenseEntity
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.PlaceListEntity
import com.cuchieman.tripplanner.data.local.TripEntity
import java.time.LocalDate

data class TripSummary(val trip: TripEntity, val placeCount: Int) {
    val start: LocalDate get() = LocalDate.ofEpochDay(trip.startDate)
    val end: LocalDate get() = LocalDate.ofEpochDay(trip.endDate)
}

/** Where a place lives: a day of the itinerary or a list. */
sealed interface PlaceTarget {
    data class Day(val epochDay: Long) : PlaceTarget
    data class InList(val listId: String) : PlaceTarget
}

val PlaceEntity.target: PlaceTarget
    get() = if (dayDate != null) PlaceTarget.Day(dayDate) else PlaceTarget.InList(listId!!)

/** Everything one trip screen needs, from one combined database flow. */
data class TripPlan(
    val trip: TripEntity,
    val lists: List<PlaceListEntity>,
    val places: List<PlaceEntity>,
    val expenses: List<ExpenseEntity>,
    val checklist: List<ChecklistItemEntity> = emptyList(),
) {
    val start: LocalDate = LocalDate.ofEpochDay(trip.startDate)
    val end: LocalDate = LocalDate.ofEpochDay(trip.endDate)

    val days: List<LocalDate> = (trip.startDate..trip.endDate).map(LocalDate::ofEpochDay)

    private val byDay: Map<Long, List<PlaceEntity>> = places
        .filter { it.dayDate != null }
        .groupBy { it.dayDate!! }
        .mapValues { (_, v) -> v.sortedBy { it.position } }

    private val byList: Map<String, List<PlaceEntity>> = places
        .filter { it.listId != null }
        .groupBy { it.listId!! }
        .mapValues { (_, v) -> v.sortedBy { it.position } }

    fun placesOn(day: LocalDate): List<PlaceEntity> = byDay[day.toEpochDay()].orEmpty()

    fun placesIn(listId: String): List<PlaceEntity> = byList[listId].orEmpty()

    /** 0-based index of the day a place is on, or null when it sits in a list. Drives pin colours. */
    fun dayIndexOf(place: PlaceEntity): Int? = place.dayDate?.let { (it - trip.startDate).toInt() }

    fun place(id: String): PlaceEntity? = places.firstOrNull { it.id == id }

    val spentMinor: Long = expenses.sumOf { it.amountMinor }

    val spentByCategory: List<Pair<String, Long>> = expenses
        .groupBy { it.category }
        .map { (category, list) -> category to list.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }
}

/** A trip just deleted, kept so the snackbar can undo it. */
data class DeletedTrip(val title: String, val backupJson: String, val coverPath: String?)
