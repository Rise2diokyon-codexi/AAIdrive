package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas

/** Draws one theme's version of the dashboard into the given canvas area. */
interface DashboardRenderer {
	fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData)
}

/** Shared color palette: black background, BMW M blue/red accents. */
object DashboardColors {
	const val BACKGROUND = 0xFF000000.toInt()
	const val FOREGROUND = 0xFFFFFFFF.toInt()
	const val DIM = 0xFF808080.toInt()
	const val M_BLUE = 0xFF0066B1.toInt()
	const val M_RED = 0xFFE4002B.toInt()
	const val WARNING = 0xFFE4002B.toInt()
}

fun rendererFor(theme: DashboardTheme): DashboardRenderer = when (theme) {
	DashboardTheme.M_CLASSIC -> MClassicDashboardRenderer()
	DashboardTheme.M_MODERN -> MModernDashboardRenderer()
}
