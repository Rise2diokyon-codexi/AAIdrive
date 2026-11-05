package me.hufman.androidautoidrive.carapp.dashboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * The six values shown on the My BMW Drive dashboard.
 * Temperatures in °C, fuel/battery as a 0-100 percentage, speed in km/h.
 */
data class DashboardData(
	val engineTempC: Float,
	val oilTempC: Float,
	val fuelPercent: Float,
	val batteryPercent: Float,
	val drivingMode: String,
	val speedKmh: Float
) {
	companion object {
		val PLACEHOLDER = DashboardData(
			engineTempC = 90f, oilTempC = 95f,
			fuelPercent = 60f, batteryPercent = 80f,
			drivingMode = "COMFORT", speedKmh = 0f
		)
	}
}

/**
 * Anything that can feed the dashboard a stream of [DashboardData].
 * [SimulatedDashboardDataSource] implements this for the prototype.
 * A future CDS-backed implementation (mirroring carinfo/CarDetailedInfo.kt, which already
 * exposes engineTemp/oilTemp/fuelLevel/accBatteryLevel/drivingMode/speedActual via CDSMetrics)
 * would combine those flows into DashboardData and implement this same interface, so the
 * rendering code below never needs to change.
 */
interface DashboardDataSource {
	val data: Flow<DashboardData>
}

/** Generates plausible, slowly-drifting values for development without a real car connection. */
class SimulatedDashboardDataSource(private val tickMs: Long = 500) : DashboardDataSource {
	private val drivingModes = listOf("ECO PRO", "COMFORT", "SPORT", "SPORT+")

	override val data: Flow<DashboardData> = flow {
		var engineTemp = 90f
		var oilTemp = 95f
		var fuel = 70f
		var battery = 85f
		var speed = 0f
		var mode = drivingModes[1]
		var ticks = 0

		while (true) {
			engineTemp = (engineTemp + Random.nextFloat() * 2 - 1).coerceIn(70f, 115f)
			oilTemp = (oilTemp + Random.nextFloat() * 2 - 1).coerceIn(75f, 120f)
			fuel = (fuel - Random.nextFloat() * 0.05f).coerceIn(0f, 100f)
			battery = (battery + Random.nextFloat() * 1.5f - 0.75f).coerceIn(0f, 100f)
			speed = (speed + Random.nextFloat() * 20 - 10).coerceIn(0f, 240f)
			if (ticks % 20 == 0) mode = drivingModes.random()

			emit(DashboardData(engineTemp, oilTemp, fuel, battery, mode, speed))
			ticks++
			delay(tickMs)
		}
	}
}
