package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Produces a bitmap independent of Android Views, suitable for future RHMI image updates.
 * The caller owns and must recycle the returned Bitmap when no longer needed.
 *
 * This is a rendering primitive, NOT a BMW/RHMI transport implementation.
 */
object DashboardBitmapRenderer {
    private const val MAX_DIMENSION = 2048

    fun render(
        width: Int,
        height: Int,
        theme: DashboardTheme,
        data: DashboardData,
        demoMode: Boolean = true
    ): Bitmap {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "Dashboard dimensions must be between 1 and $MAX_DIMENSION pixels"
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rendererFor(theme).draw(canvas, width, height, data)

        if (demoMode) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = (height * 0.045f).coerceIn(12f, 32f)
                isFakeBoldText = true
            }
            canvas.drawText("DEMO - SIMULATED DATA", 12f, paint.textSize + 8f, paint)
        }
        return bitmap
    }
}
