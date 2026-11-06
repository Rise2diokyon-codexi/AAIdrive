package me.hufman.androidautoidrive.carapp.dashboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Display units, as configured in the car */
data class DashboardUnits(
	val temperature: String = "°C",
	val speed: String = "km/h",
	val fuel: String = "L",
) {
	val isFahrenheit: Boolean get() = temperature == "°F"
	val isMph: Boolean get() = speed == "mph"

	companion object {
		val METRIC = DashboardUnits()
	}
}

/**
 * The six values shown on the My AI Drive dashboard.
 * A null value means "not reported by the car" and is drawn as unavailable, never as a guess.
 * [demo] marks simulated values, which the renderers label prominently.
 */
data class DashboardData(
	val engineTemp: Float? = null,
	val oilTemp: Float? = null,
	val fuel: Float? = null,
	val fuelRange: Float? = null,
	val battery: Float? = null,
	val drivingMode: String? = null,
	val speed: Float? = null,
	val units: DashboardUnits = DashboardUnits.METRIC,
	val demo: Boolean = false,
) {
	companion object {
		val UNAVAILABLE = DashboardData()
	}

	val hasAnyValue: Boolean
		get() = listOf(engineTemp, oilTemp, fuel, battery, speed).any { it != null } || !drivingMode.isNullOrBlank()
}

/** Formatting and gauge scales shared by both themes, kept free of Android types for unit tests */
object DashboardFormat {
	const val UNAVAILABLE = "--"

	/** F34 340i GT tank capacity, only used to scale the fuel gauge arc; the number shown is the car's value */
	const val FUEL_TANK_LITERS = 60f

	fun value(value: Float?): String = value?.roundToInt()?.toString() ?: UNAVAILABLE

	fun mode(mode: String?): String = mode?.takeIf { it.isNotBlank() }?.uppercase() ?: UNAVAILABLE

	fun temperatureRange(units: DashboardUnits): ClosedFloatingPointRange<Float> =
		if (units.isFahrenheit) 104f..266f else 40f..130f

	fun speedRange(units: DashboardUnits): ClosedFloatingPointRange<Float> =
		if (units.isMph) 0f..160f else 0f..260f

	fun fuelRange(units: DashboardUnits): ClosedFloatingPointRange<Float> = when (units.fuel) {
		"gal" -> 0f..FUEL_TANK_LITERS / 3.785f
		"gal UK" -> 0f..FUEL_TANK_LITERS / 4.546f
		else -> 0f..FUEL_TANK_LITERS
	}

	val batteryRange = 0f..100f

	/** 0..1 position of [value] within [range], or null when unavailable */
	fun ratio(value: Float?, range: ClosedFloatingPointRange<Float>): Float? {
		value ?: return null
		val span = range.endInclusive - range.start
		if (span <= 0f) return null
		return ((value - range.start) / span).coerceIn(0f, 1f)
	}

	/** Warning thresholds: hot engine/oil, low fuel, weak battery */
	fun isWarning(data: DashboardData, indicator: Indicator): Boolean = when (indicator) {
		Indicator.ENGINE_TEMP -> data.engineTemp?.let { celsius(it, data.units) >= 115f } ?: false
		Indicator.OIL_TEMP -> data.oilTemp?.let { celsius(it, data.units) >= 130f } ?: false
		Indicator.FUEL -> data.fuel?.let { (ratio(it, fuelRange(data.units)) ?: 1f) <= 0.12f } ?: false
		Indicator.BATTERY -> data.battery?.let { it <= 30f } ?: false
		else -> false
	}

	/**
	 * What the screen actually shows, rounded like the renderers round it:
	 * frames are only re-sent to the car when this changes
	 */
	fun displayKey(data: DashboardData): List<Any?> = listOf(
		value(data.engineTemp), value(data.oilTemp), value(data.fuel), value(data.fuelRange),
		value(data.battery), mode(data.drivingMode), value(data.speed), data.units, data.demo,
	)

	private fun celsius(value: Float, units: DashboardUnits) = if (units.isFahrenheit) (value - 32f) * 5f / 9f else value

	enum class Indicator { ENGINE_TEMP, OIL_TEMP, FUEL, BATTERY, DRIVING_MODE, SPEED }
}

/**
 * Anything that can feed the dashboard a stream of [DashboardData]:
 * [CdsDashboardDataSource] for the car, [SimulatedDashboardDataSource] for the demo mode.
 */
interface DashboardDataSource {
	val data: Flow<DashboardData>
}

/** Plausible drifting values for the demo mode. Every emission is flagged [DashboardData.demo]. */
class SimulatedDashboardDataSource(private val tickMs: Long = 500, private val random: Random = Random.Default) : DashboardDataSource {
	private val drivingModes = listOf("Eco Pro", "Comfort", "Sport", "Sport+")

	override val data: Flow<DashboardData> = flow {
		var engineTemp = 90f
		var oilTemp = 95f
		var fuel = 42f
		var battery = 85f
		var speed = 0f
		var mode = drivingModes[1]
		var ticks = 0

		while (true) {
			engineTemp = (engineTemp + random.nextFloat() * 2 - 1).coerceIn(70f, 112f)
			oilTemp = (oilTemp + random.nextFloat() * 2 - 1).coerceIn(75f, 120f)
			fuel = (fuel - random.nextFloat() * 0.02f).coerceIn(0f, DashboardFormat.FUEL_TANK_LITERS)
			battery = (battery + random.nextFloat() * 1.5f - 0.75f).coerceIn(40f, 100f)
			speed = (speed + random.nextFloat() * 20 - 9).coerceIn(0f, 220f)
			if (ticks % 20 == 0) mode = drivingModes[random.nextInt(drivingModes.size)]

			emit(DashboardData(engineTemp, oilTemp, fuel, fuel * 10, battery, mode, speed, demo = true))
			ticks++
			delay(tickMs)
		}
	}
}
