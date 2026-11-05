package me.hufman.androidautoidrive.carapp.dashboard

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Renders the My BMW Drive dashboard for a given [theme] and [data].
 *
 * Today this is used for on-phone preview. Later, the same drawing logic can be hosted off-screen
 * (e.g. in a VirtualDisplay, as maps/FrameUpdater.kt already does for the Maps app) and its frames
 * streamed into a car-side RHMIComponent.Image via a new view mirroring FullImageView.kt, without
 * any change to this class.
 */
class DashboardView @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

	var theme: DashboardTheme = DashboardTheme.M_CLASSIC
		set(value) { field = value; renderer = rendererFor(value); invalidate() }

	var data: DashboardData = DashboardData.PLACEHOLDER
		set(value) { field = value; invalidate() }

	private var renderer: DashboardRenderer = rendererFor(theme)

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		renderer.draw(canvas, width, height, data)
	}
}
