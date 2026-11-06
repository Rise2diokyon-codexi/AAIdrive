package me.hufman.androidautoidrive.dashboard

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.hufman.androidautoidrive.carapp.dashboard.CdsDashboardDataSource
import me.hufman.androidautoidrive.carapp.dashboard.DashboardUnits
import me.hufman.androidautoidrive.carapp.dashboard.SimulatedDashboardDataSource
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.random.Random

class DashboardDataSourceTest {
	@Test
	fun missingCdsPropertiesDoNotBlockTheOthers() = runBlocking {
		val metrics = mock<CDSMetrics> {
			on { engineTemp } doReturn flowOf(91.4)
			on { oilTemp } doReturn emptyFlow()
			on { fuelLevel } doReturn emptyFlow()
			on { totalRange } doReturn emptyFlow()
			on { accBatteryLevel } doReturn emptyFlow()
			on { drivingMode } doReturn flowOf("Sport")
			on { speedActual } doReturn flowOf(52.0)
			on { units } doReturn emptyFlow()
		}
		val data = withTimeout(5000) {
			CdsDashboardDataSource(metrics).data.first { it.speed != null && it.engineTemp != null && it.drivingMode != null }
		}
		assertEquals(91.4f, data.engineTemp!!, 0.01f)
		assertEquals(52f, data.speed!!, 0.01f)
		assertEquals("Sport", data.drivingMode)
		assertNull(data.oilTemp)
		assertNull(data.fuel)
		assertNull(data.battery)
		assertEquals(DashboardUnits.METRIC, data.units)
		assertFalse(data.demo)
	}

	@Test
	fun blankDrivingModeIsUnavailable() = runBlocking {
		val metrics = mock<CDSMetrics> {
			on { engineTemp } doReturn emptyFlow()
			on { oilTemp } doReturn emptyFlow()
			on { fuelLevel } doReturn emptyFlow()
			on { totalRange } doReturn emptyFlow()
			on { accBatteryLevel } doReturn emptyFlow()
			on { drivingMode } doReturn flowOf("")
			on { speedActual } doReturn flowOf(0.0)
			on { units } doReturn emptyFlow()
		}
		val data = withTimeout(5000) { CdsDashboardDataSource(metrics).data.first { it.speed != null } }
		assertNull(data.drivingMode)
	}

	@Test
	fun carUnitsAreMapped() {
		val units = CdsDashboardDataSource.unitsOf(CDSVehicleUnits(CDSVehicleUnits.Consumption.MPG_US,
			CDSVehicleUnits.Distance.Miles, CDSVehicleUnits.Fuel.Gallons_US, CDSVehicleUnits.Temperature.FAHRENHEIT))
		assertEquals(DashboardUnits("°F", "mph", "gal"), units)
	}

	@Test
	fun simulatedValuesAreAlwaysFlaggedAsDemo() = runBlocking {
		val values = SimulatedDashboardDataSource(tickMs = 1, random = Random(42)).data.take(30).toList()
		assertEquals(30, values.size)
		assertTrue(values.all { it.demo && it.hasAnyValue })
	}
}
