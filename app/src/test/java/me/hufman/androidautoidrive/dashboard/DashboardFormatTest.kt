package me.hufman.androidautoidrive.dashboard

import me.hufman.androidautoidrive.carapp.dashboard.DashboardData
import me.hufman.androidautoidrive.carapp.dashboard.DashboardFormat
import me.hufman.androidautoidrive.carapp.dashboard.DashboardFormat.Indicator
import me.hufman.androidautoidrive.carapp.dashboard.DashboardTheme
import me.hufman.androidautoidrive.carapp.dashboard.DashboardUnits
import org.junit.Assert.*
import org.junit.Test

class DashboardFormatTest {
	@Test
	fun unavailableValuesAreNeverGuessed() {
		assertEquals("--", DashboardFormat.value(null))
		assertEquals("--", DashboardFormat.mode(null))
		assertEquals("--", DashboardFormat.mode(" "))
		assertNull(DashboardFormat.ratio(null, 0f..100f))
		assertFalse(DashboardData.UNAVAILABLE.hasAnyValue)
		assertFalse(DashboardData.UNAVAILABLE.demo)
	}

	@Test
	fun formatsRoundedValues() {
		assertEquals("91", DashboardFormat.value(90.6f))
		assertEquals("0", DashboardFormat.value(0.2f))
		assertEquals("SPORT+", DashboardFormat.mode("Sport+"))
	}

	@Test
	fun ratioIsClampedToTheScale() {
		assertEquals(0.5f, DashboardFormat.ratio(130f, DashboardFormat.speedRange(DashboardUnits.METRIC))!!, 0.001f)
		assertEquals(0f, DashboardFormat.ratio(-5f, 0f..100f)!!, 0.001f)
		assertEquals(1f, DashboardFormat.ratio(400f, 0f..260f)!!, 0.001f)
		assertNull(DashboardFormat.ratio(5f, 10f..10f))
	}

	@Test
	fun scalesFollowCarUnits() {
		val imperial = DashboardUnits(temperature = "°F", speed = "mph", fuel = "gal")
		assertEquals(160f, DashboardFormat.speedRange(imperial).endInclusive)
		assertEquals(266f, DashboardFormat.temperatureRange(imperial).endInclusive)
		assertEquals(60f / 3.785f, DashboardFormat.fuelRange(imperial).endInclusive, 0.01f)
		assertEquals(60f, DashboardFormat.fuelRange(DashboardUnits.METRIC).endInclusive)
	}

	@Test
	fun warnings() {
		assertTrue(DashboardFormat.isWarning(DashboardData(engineTemp = 118f), Indicator.ENGINE_TEMP))
		assertFalse(DashboardFormat.isWarning(DashboardData(engineTemp = 95f), Indicator.ENGINE_TEMP))
		// 245 °F is about 118 °C
		assertTrue(DashboardFormat.isWarning(DashboardData(engineTemp = 245f, units = DashboardUnits(temperature = "°F")), Indicator.ENGINE_TEMP))
		assertTrue(DashboardFormat.isWarning(DashboardData(fuel = 5f), Indicator.FUEL))
		assertFalse(DashboardFormat.isWarning(DashboardData(fuel = 40f), Indicator.FUEL))
		assertTrue(DashboardFormat.isWarning(DashboardData(battery = 20f), Indicator.BATTERY))
		assertFalse(DashboardFormat.isWarning(DashboardData(), Indicator.BATTERY))
	}

	@Test
	fun displayKeyIgnoresInvisibleChanges() {
		val a = DashboardData(speed = 50.2f, engineTemp = 90.1f)
		val b = DashboardData(speed = 50.4f, engineTemp = 89.9f)
		val c = DashboardData(speed = 51.0f, engineTemp = 90.1f)
		assertEquals(DashboardFormat.displayKey(a), DashboardFormat.displayKey(b))
		assertNotEquals(DashboardFormat.displayKey(a), DashboardFormat.displayKey(c))
		assertNotEquals(DashboardFormat.displayKey(a), DashboardFormat.displayKey(a.copy(demo = true)))
	}

	@Test
	fun themesCycleWithTheController() {
		assertEquals(DashboardTheme.M_MODERN, DashboardTheme.M_CLASSIC.next())
		assertEquals(DashboardTheme.M_CLASSIC, DashboardTheme.M_MODERN.next())
		assertEquals(DashboardTheme.M_MODERN, DashboardTheme.M_CLASSIC.previous())
		assertEquals(DashboardTheme.M_MODERN, DashboardTheme.fromKey("M_Modern"))
		assertEquals(DashboardTheme.M_CLASSIC, DashboardTheme.fromKey("garbage"))
	}
}
