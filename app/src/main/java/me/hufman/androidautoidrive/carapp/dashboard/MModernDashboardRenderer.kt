package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import me.hufman.androidautoidrive.carapp.dashboard.DashboardFormat.Indicator

/**
 * M Modern: digital tiles. The speed tile is the largest element; on the car's wide screen it
 * takes the left side and a 3x2 grid holds the other indicators, on narrow screens it goes on top.
 */
class MModernDashboardRenderer : DashboardRenderer {
	private data class Tile(val label: String, val value: String, val unit: String, val ratio: Float?,
	                        val accent: Int, val warning: Boolean, val detail: String? = null)

	private val chrome = DashboardChrome()
	private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
	private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; letterSpacing = 0.08f }
	private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
	private val unit = Paint(Paint.ANTI_ALIAS_FLAG)

	override fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData) {
		canvas.drawColor(DashboardColors.BACKGROUND)
		val w = width.toFloat()
		val h = height.toFloat()
		val u = data.units
		val gap = minOf(w, h) * 0.025f
		val top = gap + if (data.demo) minOf(w, h) * 0.09f else 0f
		val bottom = h - minOf(w, h) * 0.07f

		val speed = Tile("VITESSE", DashboardFormat.value(data.speed), u.speed,
			DashboardFormat.ratio(data.speed, DashboardFormat.speedRange(u)), DashboardColors.M_LIGHT_BLUE, false)
		val distanceUnit = if (u.isMph) "mi" else "km"
		val others = listOf(
			Tile("MOTEUR", DashboardFormat.value(data.engineTemp), u.temperature,
				DashboardFormat.ratio(data.engineTemp, DashboardFormat.temperatureRange(u)), DashboardColors.M_RED,
				DashboardFormat.isWarning(data, Indicator.ENGINE_TEMP)),
			Tile("HUILE", DashboardFormat.value(data.oilTemp), u.temperature,
				DashboardFormat.ratio(data.oilTemp, DashboardFormat.temperatureRange(u)), DashboardColors.M_RED,
				DashboardFormat.isWarning(data, Indicator.OIL_TEMP)),
			Tile("CARBURANT", DashboardFormat.value(data.fuel), u.fuel,
				DashboardFormat.ratio(data.fuel, DashboardFormat.fuelRange(u)), DashboardColors.M_LIGHT_BLUE,
				DashboardFormat.isWarning(data, Indicator.FUEL),
				data.fuelRange?.let { "≈ ${DashboardFormat.value(it)} $distanceUnit" }),
			Tile("BATTERIE", DashboardFormat.value(data.battery), "%",
				DashboardFormat.ratio(data.battery, DashboardFormat.batteryRange), DashboardColors.M_LIGHT_BLUE,
				DashboardFormat.isWarning(data, Indicator.BATTERY)),
			Tile("MODE", DashboardFormat.mode(data.drivingMode), "", null, DashboardColors.M_DARK_BLUE, false),
			Tile("SOURCE", if (data.demo) "DÉMO" else "CDS", if (data.demo) "simulé" else "voiture", null,
				if (data.demo) DashboardColors.DEMO else DashboardColors.M_DARK_BLUE, false),
		)

		val wide = w >= h * 1.6f
		val gridRect = if (wide) {
			val speedRect = RectF(gap, top, w * 0.38f, bottom)
			drawTile(canvas, speedRect, speed, valueScale = 0.42f)
			RectF(speedRect.right + gap, top, w - gap, bottom)
		} else {
			val speedRect = RectF(gap, top, w - gap, top + (bottom - top) * 0.42f)
			drawTile(canvas, speedRect, speed, valueScale = 0.5f)
			RectF(gap, speedRect.bottom + gap, w - gap, bottom)
		}

		val columns = 3
		val rows = 2
		val tileW = (gridRect.width() - gap * (columns - 1)) / columns
		val tileH = (gridRect.height() - gap * (rows - 1)) / rows
		others.forEachIndexed { i, tile ->
			val left = gridRect.left + (i % columns) * (tileW + gap)
			val tileTop = gridRect.top + (i / columns) * (tileH + gap)
			drawTile(canvas, RectF(left, tileTop, left + tileW, tileTop + tileH), tile, valueScale = 0.36f)
		}

		chrome.drawMStripes(canvas, w - gap - w * 0.035f, top + gap * 0.6f, w * 0.006f, h * 0.035f)
		chrome.drawOverlays(canvas, width, height, data)
	}

	private fun drawTile(canvas: Canvas, rect: RectF, tile: Tile, valueScale: Float) {
		val h = rect.height()
		val pad = h * 0.1f
		fill.color = if (tile.warning) 0xFF3A0A0C.toInt() else DashboardColors.SURFACE
		canvas.drawRoundRect(rect, h * 0.06f, h * 0.06f, fill)

		// accent bar on the left edge
		fill.color = if (tile.warning) DashboardColors.WARNING else tile.accent
		canvas.drawRect(rect.left, rect.top + h * 0.12f, rect.left + h * 0.025f, rect.bottom - h * 0.12f, fill)

		val unavailable = tile.value == DashboardFormat.UNAVAILABLE
		label.color = DashboardColors.SECONDARY
		label.fitText(tile.label, h * 0.12f, rect.width() - pad * 2)
		canvas.drawText(tile.label, rect.left + pad, rect.top + pad + label.textSize, label)

		value.color = when {
			unavailable -> DashboardColors.DIM
			tile.warning -> DashboardColors.WARNING
			else -> DashboardColors.FOREGROUND
		}
		unit.color = DashboardColors.SECONDARY
		unit.textSize = h * valueScale * 0.32f
		val unitWidth = if (tile.unit.isEmpty()) 0f else unit.measureText(tile.unit) + pad * 0.5f
		value.fitText(tile.value, h * valueScale, rect.width() - pad * 2 - unitWidth)
		val baseline = rect.top + h * 0.5f + value.textSize * 0.36f
		canvas.drawText(tile.value, rect.left + pad, baseline, value)
		if (tile.unit.isNotEmpty()) {
			canvas.drawText(tile.unit, rect.left + pad + value.measureText(tile.value) + pad * 0.5f, baseline, unit)
		}

		if (tile.detail != null) {
			unit.textSize = h * 0.1f
			canvas.drawText(tile.detail, rect.left + pad, rect.bottom - pad * 1.6f, unit)
		}

		// level bar along the bottom edge
		if (tile.ratio != null || (unavailable && tile.unit.isNotEmpty())) {
			val barTop = rect.bottom - pad * 0.9f
			val barHeight = h * 0.035f
			fill.color = DashboardColors.DIM
			canvas.drawRect(rect.left + pad, barTop, rect.right - pad, barTop + barHeight, fill)
			if (tile.ratio != null) {
				fill.color = if (tile.warning) DashboardColors.WARNING else tile.accent
				canvas.drawRect(rect.left + pad, barTop, rect.left + pad + (rect.width() - pad * 2) * tile.ratio, barTop + barHeight, fill)
			}
		}
	}
}
