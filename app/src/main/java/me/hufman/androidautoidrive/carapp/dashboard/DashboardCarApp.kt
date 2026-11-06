package me.hufman.androidautoidrive.carapp.dashboard

import android.content.res.Resources
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.RHMIUtils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.deserialization.loadFromXML
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.carapp.FocusTriggerController
import me.hufman.androidautoidrive.carapp.FullImageConfig
import me.hufman.androidautoidrive.carapp.FullImageInteraction
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.maps.views.MenuView
import me.hufman.androidautoidrive.utils.removeFirst
import me.hufman.androidautoidrive.utils.removeFirstOrNull
import me.hufman.androidautoidrive.utils.Utils

/**
 * The My AI Drive dashboard as its own iDrive app, listed under Vehicle Information.
 *
 * iDrive only renders widgets that a BMW-signed UI description declares, so the dashboard
 * borrows the same description as the custom map (a plain state holding a full-screen image
 * and an invisible list): the image shows frames from [DashboardFrameStreamer], the list
 * catches the iDrive controller, which switches between the M Classic and M Modern themes.
 */
class DashboardCarApp(
	iDriveConnectionStatus: IDriveConnectionStatus,
	securityAccess: SecurityAccess,
	carAppAssets: CarAppResources,
	private val resources: Resources,
	private val appSettings: MutableAppSettings,
	fullDimensions: RHMIDimensions,
	private val streamer: DashboardFrameStreamer,
) {
	companion object {
		const val TAG = "DashboardCarApp"
		val IDENT = BuildConfig.APPLICATION_ID + ".dashboard"
		const val NAME = "My AI Drive"
	}

	private val listener = Listener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val fullImageView: FullImageView
	private val focusTriggerController: FocusTriggerController
	private val amHandle: Int

	init {
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, listener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sasChallenge = carConnection.sas_certificate(appCert)
		carConnection.sas_login(securityAccess.signChallenge(challenge = sasChallenge))
		listener.server = carConnection

		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData(IDENT, BMWRemoting.VersionInfo(0, 1, 0), IDENT, BuildConfig.APPLICATION_ID))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB("common"))
		carConnection.rhmi_initialize(rhmiHandle)

		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		listener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		val config = object : FullImageConfig {
			override val invertScroll = false
			// keep clear of the iDrive split-screen sidebar
			override val rhmiDimensions: RHMIDimensions = io.bimmergestalt.idriveconnectkit.SidebarRHMIDimensions(fullDimensions) { false }
		}
		// pick the same state the map uses: skip the map's menu state, which can also hold an image
		val unclaimedStates = java.util.LinkedList(carApp.states.values)
		unclaimedStates.removeFirstOrNull { MenuView.fits(it) }
		val dashboardState = unclaimedStates.removeFirst { FullImageView.fits(it) }
		fullImageView = FullImageView(dashboardState, NAME, config, object : FullImageInteraction {
			override fun navigateUp() = changeTheme { it.previous() }
			override fun navigateDown() = changeTheme { it.next() }
			override fun click() {}
			override fun getClickState(): RHMIState = dashboardState
		}, streamer)
		fullImageView.initWidgets()

		// the description's own entry buttons open the dashboard too
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = dashboardState.id
		}

		val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
		focusTriggerController = FocusTriggerController(focusEvent) {}

		carConnection.rhmi_addActionEventHandler(rhmiHandle, IDENT, -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, IDENT, -1, -1)

		amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
		carConnection.am_addAppEventHandler(amHandle, IDENT)
		registerAmApp()
	}

	val theme: DashboardTheme
		get() = DashboardTheme.fromKey(appSettings[AppSettings.KEYS.DASHBOARD_THEME])

	private fun changeTheme(change: (DashboardTheme) -> DashboardTheme) {
		appSettings[AppSettings.KEYS.DASHBOARD_THEME] = change(theme).key
		streamer.invalidate()
	}

	private fun registerAmApp() {
		val icon = try {
			Utils.convertPngToGrayscale(resources.openRawResource(R.drawable.ic_carinfo_common).readBytes())
		} catch (e: Resources.NotFoundException) { "" }
		val amInfo = mutableMapOf<Int, Any>(
			0 to 145,   // basecore version
			1 to NAME,
			2 to icon,
			3 to AMCategory.VEHICLE_INFORMATION.value,
			4 to true,
			5 to 750,   // weight, just above Detailed Vehicle Info
			8 to fullImageView.state.id,
		)
		for (languageCode in 101..123) {
			amInfo[languageCode] = NAME
		}
		synchronized(carConnection) {
			carConnection.am_registerApp(amHandle, IDENT, amInfo)
		}
	}

	fun onDestroy() {
		streamer.shutDown()
	}

	fun disconnect() {
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (e: java.lang.Exception) {}
	}

	inner class Listener : BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			synchronized(server!!) { }
			focusTriggerController.focusState(fullImageView.state, false)
			registerAmApp()
		}

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
				synchronized(server!!) { server?.rhmi_ackActionEvent(handle, actionId, 1, true) }
			} catch (e: RHMIActionAbort) {
				synchronized(server!!) { server?.rhmi_ackActionEvent(handle, actionId, 1, false) }
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
				synchronized(server!!) { server?.rhmi_ackActionEvent(handle, actionId, 1, true) }
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			try {
				app?.states?.get(componentId)?.onHmiEvent(eventId, args)
				app?.components?.get(componentId)?.onHmiEvent(eventId, args)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
			}
		}
	}
}
