package com.cuchieman.tripplanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables from replica/schema.sql. Dates are LocalDate.toEpochDay(), times are minutes since
// midnight, both trip-local. Money is minor units. Checks Room cannot express live in TripRepository.

@Entity(tableName = "trips", indices = [Index("start_date")])
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "destination_name") val destinationName: String,
    @ColumnInfo(name = "destination_lat") val destinationLat: Double?,
    @ColumnInfo(name = "destination_lng") val destinationLng: Double?,
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "end_date") val endDate: Long,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo(defaultValue = "") val notes: String,
    @ColumnInfo(name = "budget_minor") val budgetMinor: Long?,
    @ColumnInfo(defaultValue = "EUR") val currency: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "place_lists",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trip_id", "position")]
)
data class PlaceListEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val title: String,
    val position: Int,
    @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trip_id", "day_date", "position"), Index("list_id", "position")]
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    /** Set when the place sits in a list. Exactly one of [listId] and [dayDate] is set. */
    @ColumnInfo(name = "list_id") val listId: String?,
    /** Set when the place sits in a day of the itinerary. */
    @ColumnInfo(name = "day_date") val dayDate: Long?,
    val position: Int,
    val name: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val category: String?,
    @ColumnInfo(defaultValue = "") val note: String,
    val website: String?,
    val phone: String?,
    @ColumnInfo(name = "start_minute") val startMinute: Int?,
    @ColumnInfo(name = "end_minute") val endMinute: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("trip_id", "date"), Index("place_id")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "place_id") val placeId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(defaultValue = "otros") val category: String,
    @ColumnInfo(defaultValue = "") val description: String,
    val date: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Kept outside the entity so Room does not try to map it to a column. */
val PlaceEntity.hasCoordinates: Boolean get() = lat != null && lng != null

/** A packing or to-do item of a trip (fixes from recon: "Checklists for anything"). Added in schema v2. */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trip_id", "position")]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val text: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
