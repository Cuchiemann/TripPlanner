package com.cuchieman.tripplanner

import android.app.Application
import com.cuchieman.tripplanner.data.CoverStore
import com.cuchieman.tripplanner.data.DeletedTrip
import com.cuchieman.tripplanner.data.Geocoding
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.data.local.TripPlannerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class TripPlannerApplication : Application() {
    /** Outlives screens: used to finish saving a note after its screen is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { TripPlannerDatabase.create(this) }
    val repository by lazy { TripRepository(database) }
    val geocoding by lazy { Geocoding(this) }
    val covers by lazy { CoverStore(this) }

    /** The trip deleted last, until its undo snackbar on the home screen is gone. */
    val deletedTrip = MutableStateFlow<DeletedTrip?>(null)
}
