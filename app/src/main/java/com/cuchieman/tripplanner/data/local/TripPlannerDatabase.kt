package com.cuchieman.tripplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripEntity::class, PlaceListEntity::class, PlaceEntity::class, ExpenseEntity::class, ChecklistItemEntity::class],
    version = 2,
    exportSchema = true
)
abstract class TripPlannerDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        /** v2: checklist_items. SQL copied from app/schemas/.../2.json so it matches Room exactly. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (`id` TEXT NOT NULL, `trip_id` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `done` INTEGER NOT NULL DEFAULT 0, `position` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_trip_id_position` " +
                        "ON `checklist_items` (`trip_id`, `position`)"
                )
            }
        }

        // Every schema change gets a Migration and a MigrationTest. No destructive fallback:
        // these are the user's trips.
        fun create(context: Context): TripPlannerDatabase =
            Room.databaseBuilder(context, TripPlannerDatabase::class.java, "tripplanner.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
