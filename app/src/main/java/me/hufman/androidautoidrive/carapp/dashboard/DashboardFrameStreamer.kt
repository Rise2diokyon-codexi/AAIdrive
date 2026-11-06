package me.hufman.androidautoidrive.carapp.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.CarThreadExceptionHandler
import me.hufman.androidautoidrive.carapp.FullImageFrameSink
import java.io.ByteArrayOutputStream

/**
 * Renders the dashboard straight into a Bitmap and sends it as a JPEG to the car's image widget.
 * No VirtualDisplay needed (unlike the map): we own the drawing code.
 *
 * Only runs while the dashboard is on the car screen, and only re-sends a frame when something
 * visible changed, capped by [minFrameIntervalMs], to spare the Bluetooth link and the battery.
 */
class DashboardFrameStreamer(
	private val handler: Handler,
	private val dataSource: () -> DashboardDataSource,
	private val theme: () -> DashboardTheme,
	private val minFrameIntervalMs: Long,
	private val jpegQuality: Int,
) : FullImageFrameSink {
	companion object {
		const val TAG = "DashboardFrames"
		const val KEEPALIVE_MS = 5000L
	}

	private val scope = CoroutineScope(handler.asCoroutineDispatcher() + CarThreadExceptionHandler)
	private var job: Job? = null
	private var destination: RHMIModel? = null
	private var width = 0
	private var height = 0
	private var bitmap: Bitmap? = null
	private val output = ByteArrayOutputStream()

	private val renderers = HashMap<DashboardTheme, DashboardRenderer>()
	private var latest: DashboardData = DashboardData.UNAVAILABLE
	private var lastSentKey: Any? = null
	private var lastSentTime = 0L
	private val renderRunnable = Runnable { renderIfNeeded() }

	var framesSent = 0
		private set

	// iDrive focus events arrive on the Etch thread: do all the work on the car app's handler
	override fun showWindow(width: Int, height: Int, destination: RHMIModel) {
		handler.post { show(width, height, destination) }
	}

	override fun hideWindow(destination: RHMIModel) {
		handler.post { hide(destination) }
	}

	private fun show(width: Int, height: Int, destination: RHMIModel) {
		this.destination = destination
		this.width = width.coerceAtLeast(1)
		this.height = height.coerceAtLeast(1)
		lastSentKey = null
		Log.i(TAG, "Showing dashboard at ${this.width}x${this.height}")
		collectData()
		scheduleRender()
	}

	private var collectedSource: DashboardDataSource? = null

	private fun collectData() {
		val source = dataSource()
		if (source === collectedSource && job?.isActive == true) return
		job?.cancel()
		collectedSource = source
		latest = DashboardData.UNAVAILABLE     // never show the previous source's values as the new one's
		job = scope.launch {
			source.data.collectLatest {
				latest = it
				scheduleRender()
			}
		}
	}

	private fun hide(destination: RHMIModel) {
		if (this.destination == destination) {
			this.destination = null
			job?.cancel()
			job = null
			collectedSource = null
			handler.removeCallbacks(renderRunnable)
		}
	}

	/** Called when the theme or the demo setting changes, from the iDrive controller or the phone */
	fun invalidate() {
		handler.post {
			lastSentKey = null
			if (destination != null) {
				collectData()     // the demo setting may have switched the data source
				scheduleRender()
			}
		}
	}

	fun shutDown() {
		handler.post {
			destination?.let { hide(it) }
			bitmap?.recycle()
			bitmap = null
		}
	}

	private fun scheduleRender() {
		handler.removeCallbacks(renderRunnable)
		val wait = (lastSentTime + minFrameIntervalMs - SystemClock.uptimeMillis()).coerceAtLeast(0)
		handler.postDelayed(renderRunnable, wait)
	}

	private fun renderIfNeeded() {
		val destination = destination ?: return
		val currentTheme = theme()
		val key = DashboardFormat.displayKey(latest) to currentTheme
		val now = SystemClock.uptimeMillis()
		if (key == lastSentKey && now - lastSentTime < KEEPALIVE_MS) {
			handler.postDelayed(renderRunnable, KEEPALIVE_MS)
			return
		}
		val frame = bitmap?.takeIf { it.width == width && it.height == height }
			?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap?.recycle(); bitmap = it }
		renderers.getOrPut(currentTheme) { rendererFor(currentTheme) }.draw(Canvas(frame), width, height, latest)
		output.reset()
		frame.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
		try {
			if (destination is RHMIModel.RaImageModel) {
				destination.value = output.toByteArray()
			}
			framesSent++
			lastSentKey = key
			lastSentTime = now
		} catch (e: RuntimeException) {
			Log.w(TAG, "Failed to send dashboard frame: $e")
		} catch (e: org.apache.etch.util.TimeoutException) {
			// the phone was unplugged during a frame update
		}
		handler.postDelayed(renderRunnable, KEEPALIVE_MS)
	}
}
