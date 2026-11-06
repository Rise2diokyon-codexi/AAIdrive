package me.hufman.androidautoidrive.carapp.dashboard

import android.util.Log
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.MainService
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.cds.CDSMetrics

class DashboardAppService : CarAppService() {
	private val appSettings = AppSettingsViewer()
	private var app: DashboardCarApp? = null

	/**
	 * The dashboard borrows the custom map's RHMI identity ("smartthings" resources), which is
	 * unused in the nomap build that this edition ships. Map builds keep the map when it is enabled.
	 */
	override fun shouldStartApp(): Boolean =
		BuildConfig.FLAVOR_map == "nomap" || !appSettings[AppSettings.KEYS.ENABLED_MAPS].toBoolean()

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting My AI Drive dashboard")
		val handler = handler!!
		val settings = MutableAppSettingsReceiver(applicationContext, handler)
		val cdsSource = CdsDashboardDataSource(CDSMetrics(CarInformation()))
		val demoSource = SimulatedDashboardDataSource()
		// Bluetooth carries far less bandwidth than USB: fewer, smaller frames
		val overBluetooth = MusicAppMode.TRANSPORT_PORTS.fromPort(iDriveConnectionStatus.port) != MusicAppMode.TRANSPORT_PORTS.USB
		val streamer = DashboardFrameStreamer(handler,
			dataSource = { if (settings[AppSettings.KEYS.DASHBOARD_DEMO_MODE].toBoolean()) demoSource else cdsSource },
			theme = { DashboardTheme.fromKey(settings[AppSettings.KEYS.DASHBOARD_THEME]) },
			minFrameIntervalMs = if (overBluetooth) 500L else 200L,
			jpegQuality = if (overBluetooth) 70 else 85,
		)
		app = DashboardCarApp(iDriveConnectionStatus, securityAccess,
			CarAppAssetResources(applicationContext, "smartthings"),
			applicationContext.resources, settings,
			RHMIDimensions.create(carInformation.capabilities), streamer)
		settings.callback = { streamer.invalidate() }
	}

	override fun onCarStop() {
		app?.onDestroy()
		app?.disconnect()
		app = null
	}
}
