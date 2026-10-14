package com.cuchieman.tripplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaceCount(val tripId: String, val count: Int)

// No REPLACE anywhere: on a parent row it would delete and re-insert, cascading away the children.
@Dao
interface TripDao {
    // Trips

    @Query("SELECT * FROM trips ORDER BY start_date")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT trip_id AS tripId, COUNT(*) AS count FROM places GROUP BY trip_id")
    fun observePlaceCounts(): Flow<List<PlaceCount>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: String): TripEntity?

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET notes = :notes, updated_at = :now WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String, now: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: String)

    // Lists

    @Query("SELECT * FROM place_lists WHERE trip_id = :tripId ORDER BY position")
    fun observeLists(tripId: String): Flow<List<PlaceListEntity>>

    @Query("SELECT * FROM place_lists WHERE trip_id = :tripId AND is_default = 1 LIMIT 1")
    suspend fun getDefaultList(tripId: String): PlaceListEntity?

    @Insert
    suspend fun insertList(list: PlaceListEntity)

    @Query("SELECT * FROM place_lists WHERE id = :id")
    suspend fun getList(id: String): PlaceListEntity?

    @Query("SELECT COUNT(*) FROM place_lists WHERE trip_id = :tripId")
    suspend fun countLists(tripId: String): Int

    @Update
    suspend fun updateList(list: PlaceListEntity)

    @Query("DELETE FROM place_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    // Checklist (schema v2)

    @Query("SELECT * FROM checklist_items WHERE trip_id = :tripId ORDER BY position")
    fun observeChecklist(tripId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getChecklistItem(id: String): ChecklistItemEntity?

    @Query("SELECT COUNT(*) FROM checklist_items WHERE trip_id = :tripId")
    suspend fun countChecklist(tripId: String): Int

    @Insert
    suspend fun insertChecklistItem(item: ChecklistItemEntity)

    @Update
    suspend fun updateChecklistItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteChecklistItem(id: String)

    @Query("SELECT * FROM places WHERE id IN (:ids)")
    suspend fun getPlaces(ids: List<String>): List<PlaceEntity>

    // Places

    @Query("SELECT * FROM places WHERE trip_id = :tripId ORDER BY day_date, position")
    fun observePlaces(tripId: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlace(id: String): PlaceEntity?

    @Query("SELECT * FROM places WHERE trip_id = :tripId AND day_date = :day ORDER BY position")
    suspend fun getPlacesInDay(tripId: String, day: Long): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE list_id = :listId ORDER BY position")
    suspend fun getPlacesInList(listId: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE trip_id = :tripId AND day_date IS NOT NULL ORDER BY day_date, position")
    suspend fun getDayPlaces(tripId: String): List<PlaceEntity>

    @Insert
    suspend fun insertPlace(place: PlaceEntity)

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    @Update
    suspend fun updatePlaces(places: List<PlaceEntity>)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: String)

    // Expenses

    @Query("SELECT * FROM expenses WHERE trip_id = :tripId ORDER BY date DESC, created_at DESC")
    fun observeExpenses(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: String): ExpenseEntity?

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)
}
