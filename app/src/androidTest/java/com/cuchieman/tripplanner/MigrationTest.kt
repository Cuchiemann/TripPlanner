package com.cuchieman.tripplanner

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A phone with v1 data (the version installed before checklists) must keep every trip. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TripPlannerDatabase::class.java
    )

    @Test
    fun v1ToV2_keepsTripsAndPlaces_andAddsTheChecklist() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO trips (id, title, destination_name, destination_lat, destination_lng, start_date, end_date, " +
                    "cover_uri, notes, budget_minor, currency, created_at, updated_at) " +
                    "VALUES ('t1', 'Viejo viaje', 'Roma', NULL, NULL, 20000, 20002, NULL, 'nota', 50000, 'EUR', 0, 0)"
            )
            execSQL(
                "INSERT INTO place_lists (id, trip_id, title, position, is_default, created_at, updated_at) " +
                    "VALUES ('l1', 't1', 'Lugares por visitar', 0, 1, 0, 0)"
            )
            execSQL(
                "INSERT INTO places (id, trip_id, list_id, day_date, position, name, address, lat, lng, category, note, " +
                    "website, phone, start_minute, end_minute, created_at, updated_at) " +
                    "VALUES ('p1', 't1', NULL, 20000, 0, 'Coliseo', NULL, 41.89, 12.49, NULL, '', NULL, NULL, 540, NULL, 0, 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true, TripPlannerDatabase.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TripPlannerDatabase::class.java,
            dbName
        ).addMigrations(TripPlannerDatabase.MIGRATION_1_2).build()
        try {
            val repo = TripRepository(db)
            val plan = runBlocking { repo.observePlan("t1").first()!! }
            assertEquals("Viejo viaje", plan.trip.title)
            assertEquals(listOf("Coliseo"), plan.places.map { it.name })
            assertEquals(540, plan.places.single().startMinute)
            runBlocking { repo.addChecklistItem("t1", "Pasaporte") }
            assertEquals(1, runBlocking { repo.observePlan("t1").first()!! }.checklist.size)
        } finally {
            db.close()
        }
    }
}
