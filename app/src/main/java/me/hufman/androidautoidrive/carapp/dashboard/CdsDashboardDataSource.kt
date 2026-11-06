package me.hufman.androidautoidrive.carapp.dashboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits

/**
 * Real car values from CDS, the same properties that Detailed Vehicle Info already shows.
 * Every metric starts as null (unavailable) so one missing CDS property never blocks the others:
 * a plain combine() would wait for every flow to emit at least once.
 */
class CdsDashboardDataSource(metrics: CDSMetrics) : DashboardDataSource {
	companion object {
		fun unitsOf(units: CDSVehicleUnits) = DashboardUnits(
			temperature = when (units.temperatureUnits) {
				CDSVehicleUnits.Temperature.CELCIUS -> "°C"
				CDSVehicleUnits.Temperature.FAHRENHEIT -> "°F"
			},
			speed = when (units.distanceUnits) {
				CDSVehicleUnits.Distance.Kilometers -> "km/h"
				CDSVehicleUnits.Distance.Miles -> "mph"
			},
			fuel = when (units.fuelUnits) {
				CDSVehicleUnits.Fuel.Liters -> "L"
				CDSVehicleUnits.Fuel.Gallons_US -> "gal"
				CDSVehicleUnits.Fuel.Gallons_UK -> "gal UK"
			},
		)

		private fun <T> Flow<T>.orUnavailable(): Flow<T?> = this.map<T, T?> { it }.onStart { emit(null) }
	}

	override val data: Flow<DashboardData> = combine(listOf<Flow<Any?>>(
		metrics.engineTemp.orUnavailable(),
		metrics.oilTemp.orUnavailable(),
		metrics.fuelLevel.orUnavailable(),
		metrics.totalRange.orUnavailable(),
		metrics.accBatteryLevel.orUnavailable(),
		metrics.drivingMode.orUnavailable(),
		metrics.speedActual.orUnavailable(),
		metrics.units.orUnavailable(),
	)) { values: Array<Any?> ->
		DashboardData(
			engineTemp = (values[0] as Double?)?.toFloat(),
			oilTemp = (values[1] as Double?)?.toFloat(),
			fuel = (values[2] as Double?)?.toFloat(),
			fuelRange = (values[3] as Double?)?.toFloat(),
			battery = (values[4] as Double?)?.toFloat(),
			drivingMode = (values[5] as String?)?.takeIf { it.isNotBlank() },
			speed = (values[6] as Double?)?.toFloat(),
			units = (values[7] as CDSVehicleUnits?)?.let { unitsOf(it) } ?: DashboardUnits.METRIC,
			demo = false,
		)
	}.distinctUntilChanged()
}
