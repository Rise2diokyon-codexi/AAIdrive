package me.hufman.androidautoidrive

import android.content.Context
import android.content.Intent
import android.util.Log
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getApplicationInfoCompat

/**
 * Lets the user keep several editions (official AAIdrive, Claude Edition, GPT Edition) installed
 * while only one of them talks to the car: two editions answering the same BMW Connected
 * session would register duplicate RHMI apps and fight over the car's audio context.
 */
object EditionCarConnection {
	private const val TAG = "EditionCarConnection"

	/** Package names of the other editions that use the same car integration */
	val OTHER_EDITIONS = mapOf(
		"me.hufman.androidautoidrive" to "AAIdrive (officiel)",
		"com.myaidrive.gpt" to "My AI Drive GPT Edition",
		"com.myaidrive.claude" to "My AI Drive Claude Edition",
	)

	fun isEnabled(context: Context): Boolean {
		AppSettings.loadSettings(context.applicationContext)
		return AppSettings[AppSettings.KEYS.CAR_CONNECTION_ENABLED].toBoolean()
	}

	fun setEnabled(context: Context, enabled: Boolean) {
		val appContext = context.applicationContext
		MutableAppSettingsReceiver(appContext)[AppSettings.KEYS.CAR_CONNECTION_ENABLED] = enabled.toString()
		if (enabled) {
			startService(appContext)
		} else {
			// onDestroy disconnects every car app and releases the car connection
			appContext.stopService(Intent(appContext, MainService::class.java))
		}
	}

	/** Starts the car connection service, unless the user switched this edition off */
	fun startService(context: Context) {
		if (!isEnabled(context)) {
			Log.i(TAG, "Car connection is disabled for this edition, not starting MainService")
			return
		}
		try {
			val intent = Intent(context, MainService::class.java).setAction(MainService.ACTION_START)
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				context.startForegroundService(intent)
			} else {
				context.startService(intent)
			}
		} catch (e: IllegalStateException) {
			// background start restrictions, the car connection broadcast will retry
			Log.w(TAG, "Could not start MainService: $e")
		}
	}

	fun installedOtherEditions(context: Context): List<String> {
		val pm = context.packageManager
		return OTHER_EDITIONS.filterKeys { it != context.packageName }
			.filterKeys { pm.getApplicationInfoCompat(it) != null }
			.values.toList()
	}
}
