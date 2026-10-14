package com.cuchieman.tripplanner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cuchieman.tripplanner.data.GeoResult
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.ui.screens.trip.TripViewModel
import com.cuchieman.tripplanner.ui.screens.create.CreateTripViewModel
import com.cuchieman.tripplanner.ui.screens.expense.ExpenseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** A fast double tap on a submit button must not create two rows. */
@RunWith(AndroidJUnit4::class)
class DoubleSubmitTest {
    private val app get() = ApplicationProvider.getApplicationContext<TripPlannerApplication>()
    private val main = InstrumentationRegistry.getInstrumentation()

    @Before
    fun emptyDatabase() = app.database.clearAllTables()

    @Test
    fun createTrip_tappedTwice_createsOneTrip() {
        val vm = CreateTripViewModel(app.repository, app.geocoding)
        val start = LocalDate.now()
        main.runOnMainSync {
            vm.choose(GeoResult("Oporto", null, 41.15, -8.61))
            vm.onDates(start, start.plusDays(2))
            vm.create {}
            vm.create {}
        }
        Thread.sleep(1_000)
        assertEquals(1, runBlocking { app.repository.observeTripSummaries().first() }.size)
    }

    private fun tripVm(tripId: String): TripViewModel {
        lateinit var vm: TripViewModel
        main.runOnMainSync {
            vm = TripViewModel(tripId, app.repository, app.geocoding, app.appScope, app.covers, app.deletedTrip)
        }
        return vm
    }

    @Test
    fun addPlaceByHand_tappedTwice_addsOnePlace() {
        val start = LocalDate.now()
        val tripId = runBlocking { app.repository.createTrip("x", "Oporto", null, null, start, start) }
        val vm = tripVm(tripId)
        val day = PlaceTarget.Day(start.toEpochDay())
        main.runOnMainSync {
            vm.addManual(day, "Café")
            vm.addManual(day, "Café")
        }
        Thread.sleep(1_000)
        assertEquals(1, runBlocking { app.repository.observePlan(tripId).first()!! }.places.size)
    }

    @Test
    fun createList_tappedTwice_makesOneList() {
        val start = LocalDate.now()
        val tripId = runBlocking { app.repository.createTrip("x", "Oporto", null, null, start, start) }
        val vm = tripVm(tripId)
        main.runOnMainSync {
            vm.createList("Para comer")
            vm.createList("Para comer")
        }
        Thread.sleep(1_000)
        assertEquals(2, runBlocking { app.repository.observePlan(tripId).first()!! }.lists.size) // default + 1
    }

    @Test
    fun saveExpense_tappedTwice_savesOneExpense() {
        val start = LocalDate.now()
        val tripId = runBlocking { app.repository.createTrip("x", "Oporto", null, null, start, start) }
        lateinit var vm: ExpenseViewModel
        main.runOnMainSync { vm = ExpenseViewModel(tripId, null, null, app.repository) }
        // The plan flow only starts when collected; wait until it has a value.
        runBlocking { vm.plan.first { it != null } }
        Thread.sleep(300)
        main.runOnMainSync {
            vm.onAmount("12")
            vm.save {}
            vm.save {}
        }
        Thread.sleep(1_000)
        assertEquals(1, runBlocking { app.repository.observePlan(tripId).first()!! }.expenses.size)
    }
}
