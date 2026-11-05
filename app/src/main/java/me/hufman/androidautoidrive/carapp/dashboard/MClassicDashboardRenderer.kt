package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * M Classic: traditional BMW instrument-cluster look.
 * Black background, white circular dial needles/arcs, a large central speed dial.
 */
class MClassicDashboardRenderer : DashboardRenderer {
	// gauges sweep from -210° (min) to +30° (max), like a classic BMW dial
	private val sweepStart = -210f
	private val sweepAngle = 240f

	override fun draw(canvas: Canvas, width: Int, height: Int, data: DashboardData) {
		canvas.drawColor(DashboardColors.BACKGROUND)

		val unit = min(width, height) / 100f
		val speedRadius = 32f * unit
		val smallRadius = 15f * unit
		val cx = width / 2f
		val cy = height / 2f

		drawGauge(canvas, cx, cy, speedRadius, data.speedKmh, 0f, 240f,
			"km/h", data.speedKmh.toInt().toString(), DashboardColors.FOREGROUND, textScale = 2.4f)

		drawGauge(canvas, speedRadius + smallRadius * 1.3f, cy - speedRadius * 0.9f, smallRadius,
			data.engineTempC, 40f, 130f, "ENGINE °C", data.engineTempC.toInt().toString(), DashboardColors.M_BLUE)
		drawGauge(canvas, speedRadius + smallRadius * 1.3f, cy + speedRadius * 0.9f, smallRadius,
			data.oilTempC, 40f, 130f, "OIL °C", data.oilTempC.toInt().toString(), DashboardColors.M_BLUE)
		drawGauge(canvas, width - speedRadius - smallRadius * 1.3f, cy - speedRadius * 0.9f, smallRadius,
			data.fuelPercent, 0f, 100f, "FUEL %", data.fuelPercent.toInt().toString(), DashboardColors.M_RED)
		drawGauge(canvas, width - speedRadius - smallRadius * 1.3f, cy + speedRadius * 0.9f, smallRadius,
			data.batteryPercent, 0f, 100f, "BATTERY %", data.batteryPercent.toInt().toString(), DashboardColors.M_RED)

		val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = DashboardColors.FOREGROUND
			textSize = 5f * unit
			textAlign = Paint.Align.CENTER
			isFakeBoldText = true
		}
		canvas.drawText(data.drivingMode, cx, cy + speedRadius + 7f * unit, modePaint)
	}

	private fun drawGauge(
		canvas: Canvas, cx: Float, cy: Float, radius: Float,
		value: Float, minValue: Float, maxValue: Float,
		label: String, valueText: String, accentColor: Int, textScale: Float = 1f
	) {
		val ratio = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
		val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

		val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeWidth = radius * 0.12f
			color = DashboardColors.DIM
			strokeCap = Paint.Cap.ROUND
		}
		canvas.drawArc(bounds, sweepStart, sweepAngle, false, trackPaint)

		val valuePaint = Paint(trackPaint).apply { color = accentColor }
		canvas.drawArc(bounds, sweepStart, sweepAngle * ratio, false, valuePaint)

		val valuePaintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = DashboardColors.FOREGROUND
			textAlign = Paint.Align.CENTER
			textSize = radius * 0.55f * textScale
			isFakeBoldText = true
		}
		canvas.drawText(valueText, cx, cy + valuePaintText.textSize * 0.35f, valuePaintText)

		val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = DashboardColors.DIM
			textAlign = Paint.Align.CENTER
			textSize = radius * 0.18f
		}
		canvas.drawText(label, cx, cy + radius * 0.65f, labelPaint)
	}
}
