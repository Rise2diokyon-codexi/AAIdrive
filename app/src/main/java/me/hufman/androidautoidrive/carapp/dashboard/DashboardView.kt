package me.hufman.androidautoidrive.carapp.dashboard

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Phone-side preview of the dashboard, drawn with the same renderers that produce the
 * images sent to the car by [DashboardFrameStreamer].
 */
class DashboardView @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

	var theme: DashboardTheme = DashboardTheme.M_CLASSIC
		set(value) { field = value; renderer = rendererFor(value); invalidate() }

	var data: DashboardData = DashboardData.UNAVAILABLE
		set(value) { field = value; invalidate() }

	private var renderer: DashboardRenderer = rendererFor(theme)

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		renderer.draw(canvas, width, height, data)
	}
}
