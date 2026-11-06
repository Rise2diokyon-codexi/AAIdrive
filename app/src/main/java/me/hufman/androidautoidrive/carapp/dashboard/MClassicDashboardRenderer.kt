package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import me.hufman.androidautoidrive.carapp.dashboard.DashboardFormat.Indicator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * M Classic: traditional BMW instrument cluster.
 * Black background, white tick marks, red needles, a large central speedometer with its
 * digital readout in the lower gap of the dial, four smaller dials around it.
 */
class MClassicDashboardRenderer : DashboardRenderer {
	companion object {
		// dial from 7:30 to 4:30, like BMW clusters, leaving the bottom quadrant for the digital value
		const val SWEEP_START = 135f
		const val SWEEP = 270f
	}

	private val chrome = DashboardChrome()
	private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
	private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
	private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
	private val arcBounds = RectF()

	override fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData) {
		canvas.drawColor(DashboardColors.BACKGROUND)
		val w = width.toFloat()
		val h = height.toFloat()
		val units = data.units

		val speedR = min(h * 0.39f, w * 0.2f)
		val cx = w / 2f
		val cy = h * 0.47f
		var smallR = min(h * 0.19f, w * 0.095f)
		// keep the side columns on screen for narrow (phone preview) proportions
		val maxSmallR = (w / 2f - speedR) / 2.6f
		smallR = min(smallR, maxSmallR).coerceAtLeast(1f)
		val columnOffset = speedR + smallR * 1.45f
		val rowTop = h * 0.27f
		val rowBottom = h * 0.70f

		chrome.drawMStripes(canvas, w * 0.03f, h * 0.04f, w * 0.006f, h * 0.05f)

		val speedRange = DashboardFormat.speedRange(units)
		drawDial(canvas, cx, cy, speedR, DashboardFormat.ratio(data.speed, speedRange),
			DashboardFormat.value(data.speed), units.speed, null, warning = false,
			tickValues = (0..speedRange.endInclusive.toInt() step 40).toList(), scaleMax = speedRange.endInclusive,
			majorTicks = speedRange.endInclusive.toInt() / 20)

		val tempRange = DashboardFormat.temperatureRange(units)
		drawDial(canvas, cx - columnOffset, rowTop, smallR, DashboardFormat.ratio(data.engineTemp, tempRange),
			DashboardFormat.value(data.engineTemp), units.temperature, "MOTEUR", DashboardFormat.isWarning(data, Indicator.ENGINE_TEMP))
		drawDial(canvas, cx - columnOffset, rowBottom, smallR, DashboardFormat.ratio(data.oilTemp, tempRange),
			DashboardFormat.value(data.oilTemp), units.temperature, "HUILE", DashboardFormat.isWarning(data, Indicator.OIL_TEMP))
		drawDial(canvas, cx + columnOffset, rowTop, smallR, DashboardFormat.ratio(data.fuel, DashboardFormat.fuelRange(units)),
			DashboardFormat.value(data.fuel), units.fuel, "CARBURANT", DashboardFormat.isWarning(data, Indicator.FUEL))
		drawDial(canvas, cx + columnOffset, rowBottom, smallR, DashboardFormat.ratio(data.battery, DashboardFormat.batteryRange),
			DashboardFormat.value(data.battery), "%", "BATTERIE", DashboardFormat.isWarning(data, Indicator.BATTERY))

		drawModePill(canvas, cx, cy + speedR + h * 0.045f, speedR, h * 0.06f, DashboardFormat.mode(data.drivingMode))
		chrome.drawOverlays(canvas, width, height, data)
	}

	private fun angleAt(ratio: Float) = Math.toRadians((SWEEP_START + SWEEP * ratio).toDouble())

	private fun drawDial(canvas: Canvas, cx: Float, cy: Float, r: Float, ratio: Float?,
	                     valueText: String, unitText: String, label: String?, warning: Boolean,
	                     tickValues: List<Int> = emptyList(), scaleMax: Float = 1f, majorTicks: Int = 6) {
		arcBounds.set(cx - r, cy - r, cx + r, cy + r)

		// bezel and scale
		stroke.color = DashboardColors.DIM
		stroke.strokeWidth = r * 0.035f
		canvas.drawArc(arcBounds, SWEEP_START, SWEEP, false, stroke)
		val minorPerMajor = 2
		val totalTicks = majorTicks * minorPerMajor
		for (i in 0..totalTicks) {
			val major = i % minorPerMajor == 0
			val angle = angleAt(i.toFloat() / totalTicks)
			val inner = r * (if (major) 0.82f else 0.89f)
			stroke.color = if (major) DashboardColors.FOREGROUND else DashboardColors.SECONDARY
			stroke.strokeWidth = r * (if (major) 0.03f else 0.015f)
			canvas.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
				cx + cos(angle).toFloat() * r * 0.97f, cy + sin(angle).toFloat() * r * 0.97f, stroke)
		}
		if (tickValues.isNotEmpty()) {
			text.color = DashboardColors.SECONDARY
			text.isFakeBoldText = false
			text.textSize = r * 0.11f
			tickValues.forEach { tickValue ->
				val angle = angleAt(tickValue / scaleMax)
				canvas.drawText(tickValue.toString(), cx + cos(angle).toFloat() * r * 0.68f,
					cy + sin(angle).toFloat() * r * 0.68f + text.textSize * 0.35f, text)
			}
		}

		val accent = if (warning) DashboardColors.WARNING else DashboardColors.M_LIGHT_BLUE
		if (ratio != null) {
			// value arc just inside the bezel, then the needle
			stroke.color = accent
			stroke.strokeWidth = r * 0.05f
			arcBounds.set(cx - r * 0.93f, cy - r * 0.93f, cx + r * 0.93f, cy + r * 0.93f)
			canvas.drawArc(arcBounds, SWEEP_START, SWEEP * ratio, false, stroke)

			val angle = angleAt(ratio)
			stroke.color = if (warning) DashboardColors.WARNING else DashboardColors.M_RED
			stroke.strokeWidth = r * 0.045f
			canvas.drawLine(cx - cos(angle).toFloat() * r * 0.12f, cy - sin(angle).toFloat() * r * 0.12f,
				cx + cos(angle).toFloat() * r * 0.8f, cy + sin(angle).toFloat() * r * 0.8f, stroke)
			fill.color = DashboardColors.SURFACE
			canvas.drawCircle(cx, cy, r * 0.09f, fill)
		}

		if (label != null) {
			text.color = DashboardColors.SECONDARY
			text.isFakeBoldText = true
			text.fitText(label, r * 0.15f, r * 1.1f)
			canvas.drawText(label, cx, cy - r * 0.32f, text)
		}

		// digital value in the bottom gap of the dial
		text.color = when {
			ratio == null -> DashboardColors.DIM
			warning -> DashboardColors.WARNING
			else -> DashboardColors.FOREGROUND
		}
		text.isFakeBoldText = true
		text.fitText(valueText, r * 0.36f, r * 1.2f)
		canvas.drawText(valueText, cx, cy + r * 0.62f, text)
		text.color = DashboardColors.SECONDARY
		text.isFakeBoldText = false
		text.textSize = r * 0.13f
		canvas.drawText(unitText, cx, cy + r * 0.84f, text)
	}

	private fun drawModePill(canvas: Canvas, cx: Float, cy: Float, maxWidth: Float, pillHeight: Float, mode: String) {
		text.isFakeBoldText = true
		text.fitText(mode, pillHeight * 0.62f, maxWidth)
		val halfWidth = text.measureText(mode) / 2f + pillHeight * 0.6f
		arcBounds.set(cx - halfWidth, cy - pillHeight / 2f, cx + halfWidth, cy + pillHeight / 2f)
		stroke.color = if (mode == DashboardFormat.UNAVAILABLE) DashboardColors.DIM else DashboardColors.M_LIGHT_BLUE
		stroke.strokeWidth = pillHeight * 0.06f
		canvas.drawRoundRect(arcBounds, pillHeight / 2f, pillHeight / 2f, stroke)
		text.color = if (mode == DashboardFormat.UNAVAILABLE) DashboardColors.DIM else DashboardColors.FOREGROUND
		canvas.drawText(mode, cx, cy + text.textSize * 0.35f, text)
	}
}
