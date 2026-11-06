package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * M Modern: digital tile grid. Black background, blue/red M accent bars.
 * The speed tile spans the top and is the largest, most legible element.
 */
class MModernDashboardRenderer : DashboardRenderer {
	private val tileGap = 0.015f // fraction of min(width, height)

	override fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData) {
		canvas.drawColor(DashboardColors.BACKGROUND)
		val gap = tileGap * minOf(width, height)

		val speedTile = RectF(gap, gap, width - gap, height * 0.5f)
		drawTile(canvas, speedTile, "SPEED", "${data.speedKmh.toInt()}", "km/h",
			DashboardColors.M_BLUE, valueTextFraction = 0.5f)

		val tiles = listOf(
			Triple("ENGINE", "${data.engineTempC.toInt()}°C", DashboardColors.M_RED),
			Triple("OIL", "${data.oilTempC.toInt()}°C", DashboardColors.M_RED),
			Triple("FUEL", "${data.fuelPercent.toInt()}%", DashboardColors.M_BLUE),
			Triple("BATTERY", "${data.batteryPercent.toInt()}%", DashboardColors.M_BLUE),
			Triple("MODE", data.drivingMode, DashboardColors.M_RED)
		)

		// Five narrow columns clipped values on common 1280x480 iDrive displays.
		// Use a balanced 3+2 layout instead, with legible labels and driving mode.
		val gridTop = height * 0.5f + gap
		val gridBottom = height - gap
		val rowHeight = (gridBottom - gridTop - gap) / 2f
		tiles.forEachIndexed { index, (label, value, accent) ->
			val row = if (index < 3) 0 else 1
			val columns = if (row == 0) 3 else 2
			val column = if (row == 0) index else index - 3
			val tileWidth = (width - (columns + 1) * gap) / columns
			val left = gap + column * (tileWidth + gap)
			val top = gridTop + row * (rowHeight + gap)
			val rect = RectF(left, top, left + tileWidth, top + rowHeight)
			drawTile(canvas, rect, label, value, "", accent, valueTextFraction = 0.30f)
		}
	}

	private fun drawTile(
		canvas: Canvas, rect: RectF, label: String, value: String, unit: String,
		accentColor: Int, valueTextFraction: Float
	) {
		val tilePaint = Paint().apply { color = 0xFF101010.toInt() }
		canvas.drawRoundRect(rect, rect.height() * 0.04f, rect.height() * 0.04f, tilePaint)

		val accentPaint = Paint().apply { color = accentColor }
		val accentHeight = rect.height() * 0.05f
		canvas.drawRect(rect.left, rect.top, rect.right, rect.top + accentHeight, accentPaint)

		val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = DashboardColors.DIM
			textSize = rect.height() * 0.12f
			textAlign = Paint.Align.CENTER
			isFakeBoldText = true
		}
		canvas.drawText(label, rect.centerX(), rect.top + rect.height() * 0.28f, labelPaint)

		val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = DashboardColors.FOREGROUND
			textSize = rect.height() * valueTextFraction
			textAlign = Paint.Align.CENTER
			isFakeBoldText = true
		}
		val valueY = rect.centerY() + valuePaint.textSize * 0.4f
		canvas.drawText(value, rect.centerX(), valueY, valuePaint)

		if (unit.isNotEmpty()) {
			val unitPaint = Paint(labelPaint).apply { textSize = rect.height() * 0.08f }
			canvas.drawText(unit, rect.centerX(), rect.bottom - rect.height() * 0.08f, unitPaint)
		}
	}
}
