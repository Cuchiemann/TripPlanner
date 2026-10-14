package com.cuchieman.tripplanner.data

import java.time.LocalDate

/**
 * A ready-made trip so every screen has real content on a fresh install.
 * Offered from the home empty state ("Probar con un viaje de ejemplo"). Text written for this app.
 */
suspend fun TripRepository.createSampleTrip(today: LocalDate = LocalDate.now()): String {
    val start = today.plusDays(14)
    val end = start.plusDays(3)
    val tripId = createTrip("Lisboa en cuatro días", "Lisboa, Portugal", 38.7223, -9.1393, start, end)
    updateNotes(
        tripId,
        "• Comprar la tarjeta de transporte en el aeropuerto\n• Las cuestas son serias: calzado cómodo\n• Muchos museos cierran los lunes"
    )

    val day1 = PlaceTarget.Day(start.toEpochDay())
    val day2 = PlaceTarget.Day(start.plusDays(1).toEpochDay())
    val day3 = PlaceTarget.Day(start.plusDays(2).toEpochDay())

    val torre = addPlace(tripId, day1, "Torre de Belém", "Av. Brasília, Lisboa", 38.6916, -9.2160, "Llegar antes de las 10 para evitar la cola.")
    setPlaceTime(torre, 9 * 60 + 30, 11 * 60)
    addPlace(tripId, day1, "Monasterio de los Jerónimos", "Praça do Império, Lisboa", 38.6979, -9.2065)
    addPlace(tripId, day1, "Pastelería en Belém", "Rua de Belém, Lisboa", 38.6975, -9.2032, "Probar los pasteles de nata recién hechos.")

    addPlace(tripId, day2, "Castillo de San Jorge", "Rua de Santa Cruz do Castelo, Lisboa", 38.7139, -9.1335)
    addPlace(tripId, day2, "Mirador de Santa Luzia", "Largo Santa Luzia, Lisboa", 38.7118, -9.1301)
    val fado = addPlace(tripId, day2, "Cena con fado en Alfama", "Alfama, Lisboa", 38.7115, -9.1300)
    setPlaceTime(fado, 20 * 60 + 30, null)

    addPlace(tripId, day3, "Excursión a Sintra", "Sintra, Portugal", 38.7979, -9.3881, "Tren desde la estación del Rossio.")

    // No coordinates on purpose: a manual entry, shown in lists but not on the map.
    addPlace(tripId, PlaceTarget.InList(defaultListId(tripId)), "Tienda de conservas que recomendó Ana")
    addPlace(tripId, PlaceTarget.InList(defaultListId(tripId)), "LX Factory", "Rua Rodrigues de Faria, Lisboa", 38.7036, -9.1786)

    val passport = addChecklistItem(tripId, "DNI o pasaporte")
    setChecklistDone(passport, true)
    addChecklistItem(tripId, "Calzado cómodo para las cuestas")
    addChecklistItem(tripId, "Cargador y batería externa")

    setBudget(tripId, 80_000)
    saveExpense(null, tripId, 32_000, "alojamiento", "Apartamento, 3 noches", null, start)
    saveExpense(null, tripId, 1_850, "comida", "Comida en el mercado", null, start)
    saveExpense(null, tripId, 1_200, "actividades", "Entrada a la Torre", torre, start)
    saveExpense(null, tripId, 640, "transporte", "Tarjeta de transporte", null, start)

    return tripId
}
