package me.hufman.androidautoidrive.carapp.dashboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.hufman.androidautoidrive.cds.CDSMetrics

/**
 * Real CDS measurements, deliberately kept separate from simulated DashboardData.
 *
 * A null measurement means the vehicle has not supplied that value yet.
 * In particular, CDSMetrics.fuelLevel is a *volume*, not a tank percentage.
 * Converting it to a percentage requires a verified tank capacity for the car.
 */
data class CDSDashboardSnapshot(
    val engineTemperature: Double?,
    val oilTemperature: Double?,
    val fuelVolume: Double?,
    val accessoryBatteryPercent: Double?,
    val drivingMode: String?,
    val speed: Double?
)

/**
 * Bridges the same CDSMetrics feeds already used by CarDetailedInfo.
 * The renderer must respect vehicle unit preferences before labeling °C or km/h.
 */
class CDSDashboardDataSource(metrics: CDSMetrics) {
    val snapshots: Flow<CDSDashboardSnapshot> = combine(
        metrics.engineTemp.map { it as Double? },
        metrics.oilTemp.map { it as Double? },
        metrics.fuelLevel.map { it as Double? },
        metrics.accBatteryLevel.map { it as Double? },
        metrics.drivingMode.map { it as String? },
        metrics.speedActual.map { it as Double? }
    ) { values ->
        CDSDashboardSnapshot(
            engineTemperature = values[0] as Double?,
            oilTemperature = values[1] as Double?,
            fuelVolume = values[2] as Double?,
            accessoryBatteryPercent = values[3] as Double?,
            drivingMode = values[4] as String?,
            speed = values[5] as Double?
        )
    }
}
