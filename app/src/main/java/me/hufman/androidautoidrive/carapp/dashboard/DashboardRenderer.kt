package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Draws one theme's version of the dashboard into the given canvas area. */
interface DashboardRenderer {
	fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData)
}

/** Shared palette: black background, BMW M stripe colours, amber for the demo banner. */
object DashboardColors {
	const val BACKGROUND = 0xFF000000.toInt()
	const val SURFACE = 0xFF111317.toInt()
	const val FOREGROUND = 0xFFFFFFFF.toInt()
	const val SECONDARY = 0xFFB4B8C0.toInt()
	const val DIM = 0xFF4A4E57.toInt()
	const val M_LIGHT_BLUE = 0xFF4AA3DF.toInt()
	const val M_DARK_BLUE = 0xFF1C3F94.toInt()
	const val M_RED = 0xFFE4002B.toInt()
	const val WARNING = 0xFFFF3B30.toInt()
	const val DEMO = 0xFFFFB300.toInt()
}

fun rendererFor(theme: DashboardTheme): DashboardRenderer = when (theme) {
	DashboardTheme.M_CLASSIC -> MClassicDashboardRenderer()
	DashboardTheme.M_MODERN -> MModernDashboardRenderer()
}

/** Elements both themes draw the same way: M stripes, demo banner, waiting message, footer */
internal class DashboardChrome {
	companion object {
		const val FOOTER = "My AI Drive · Claude Edition"
		const val DEMO_TEXT = "DÉMO — VALEURS SIMULÉES"
		const val WAITING_TEXT = "En attente des données du véhicule…"
	}

	private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
	private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

	fun drawMStripes(canvas: Canvas, left: Float, top: Float, stripeWidth: Float, stripeHeight: Float) {
		listOf(DashboardColors.M_LIGHT_BLUE, DashboardColors.M_DARK_BLUE, DashboardColors.M_RED).forEachIndexed { i, color ->
			fill.color = color
			val x = left + i * stripeWidth * 1.4f
			canvas.drawRect(x, top, x + stripeWidth, top + stripeHeight, fill)
		}
	}

	/** Drawn last, over the gauges, so simulated values can never be mistaken for measurements */
	fun drawOverlays(canvas: Canvas, width: Int, height: Int, data: DashboardData) {
		val unit = minOf(width, height) / 100f
		if (data.demo) {
			val bandHeight = 9f * unit
			fill.color = DashboardColors.DEMO
			canvas.drawRect(0f, 0f, width.toFloat(), bandHeight, fill)
			text.color = DashboardColors.BACKGROUND
			text.textSize = bandHeight * 0.62f
			canvas.drawText(DEMO_TEXT, width / 2f, bandHeight * 0.74f, text)
		} else if (!data.hasAnyValue) {
			text.color = DashboardColors.SECONDARY
			text.textSize = 5f * unit
			canvas.drawText(WAITING_TEXT, width / 2f, height - 9f * unit, text)
		}
		text.color = DashboardColors.DIM
		text.textSize = 3.6f * unit
		canvas.drawText(FOOTER, width / 2f, height - 2.2f * unit, text)
	}
}

/** Shrinks the paint's text size until [value] fits in [maxWidth] */
internal fun Paint.fitText(value: String, desiredSize: Float, maxWidth: Float) {
	textSize = desiredSize
	val measured = measureText(value)
	if (measured > maxWidth && measured > 0f) {
		textSize = desiredSize * maxWidth / measured
	}
}

internal fun RectF.inset(amount: Float) = RectF(left + amount, top + amount, right - amount, bottom - amount)
