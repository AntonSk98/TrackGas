package com.trackgas.app.temperature

/** Groups the collaborators the tracker needs for meter location and temperature features. */
class TemperatureServices(
    val locationResolver: LocationResolver,
    val locationStore: MeterLocationStore,
    val networkMonitor: NetworkMonitor,
    val temperatures: TemperatureRepository,
    val checks: TemperatureCheckRequester,
    val coordinator: TemperatureCoordinator = TemperatureCoordinator(locationStore, temperatures),
)
