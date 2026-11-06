package me.hufman.androidautoidrive.phoneui.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.EditionCarConnection
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.dashboard.DashboardData
import me.hufman.androidautoidrive.carapp.dashboard.DashboardTheme
import me.hufman.androidautoidrive.carapp.dashboard.DashboardView
import me.hufman.androidautoidrive.carapp.dashboard.SimulatedDashboardDataSource
import me.hufman.androidautoidrive.edition.AndroidEditionEnvironment
import me.hufman.androidautoidrive.edition.EditionDiagnostics

/**
 * "Édition & diagnostic": which edition is talking to the car, the dashboard settings,
 * and the Android conditions the car integration depends on, with shortcuts to fix them.
 */
class EditionDiagnosticsFragment : Fragment() {
	private val appSettings by lazy { MutableAppSettingsReceiver(requireContext().applicationContext) }
	private var previewJob: Job? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		inflater.inflate(R.layout.fragment_edition_diagnostics, container, false)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val swCarConnection = view.findViewById<SwitchCompat>(R.id.swCarConnection)
		swCarConnection.setOnCheckedChangeListener { button, checked ->
			if (button.isPressed) {
				EditionCarConnection.setEnabled(requireContext(), checked)
				refresh()
			}
		}

		val grpTheme = view.findViewById<RadioGroup>(R.id.grpDashboardTheme)
		grpTheme.setOnCheckedChangeListener { _, checkedId ->
			val theme = if (checkedId == R.id.rdoThemeModern) DashboardTheme.M_MODERN else DashboardTheme.M_CLASSIC
			appSettings[AppSettings.KEYS.DASHBOARD_THEME] = theme.key
			view.findViewById<DashboardView>(R.id.dashboardPreview).theme = theme
		}

		val swDemo = view.findViewById<SwitchCompat>(R.id.swDemoMode)
		swDemo.setOnCheckedChangeListener { button, checked ->
			if (button.isPressed) {
				appSettings[AppSettings.KEYS.DASHBOARD_DEMO_MODE] = checked.toString()
				updatePreview()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		AppSettings.loadSettings(requireContext().applicationContext)
		refresh()
		updatePreview()
	}

	override fun onPause() {
		super.onPause()
		previewJob?.cancel()
	}

	/** The phone preview never invents values: real data only exists in the car, so outside the demo it shows the unavailable state */
	private fun updatePreview() {
		val view = view ?: return
		val preview = view.findViewById<DashboardView>(R.id.dashboardPreview)
		previewJob?.cancel()
		if (appSettings[AppSettings.KEYS.DASHBOARD_DEMO_MODE].toBoolean()) {
			previewJob = viewLifecycleOwner.lifecycleScope.launch {
				SimulatedDashboardDataSource().data.collectLatest { preview.data = it }
			}
		} else {
			preview.data = DashboardData.UNAVAILABLE
		}
	}

	private fun refresh() {
		val view = view ?: return
		val context = requireContext()
		val environment = AndroidEditionEnvironment(context)
		val diagnostics = EditionDiagnostics(environment)

		view.findViewById<TextView>(R.id.txtEditionIdentity).text = listOf(
			"Édition : ${BuildConfig.EDITION_NAME}",
			"Package : ${context.packageName}",
			"Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}",
			"Installé par : ${environment.installerPackage ?: "inconnu (APK manuel/adb)"}",
			"Signature SHA-1 : ${environment.signingFingerprint("SHA-1") ?: "?"}",
			"Signature SHA-256 : ${environment.signingFingerprint("SHA-256") ?: "?"}",
			"Redirection Spotify : ${diagnostics.spotifyRedirectUri}",
		).joinToString("\n")

		view.findViewById<SwitchCompat>(R.id.swCarConnection).isChecked = environment.carConnectionEnabled
		val theme = DashboardTheme.fromKey(appSettings[AppSettings.KEYS.DASHBOARD_THEME])
		view.findViewById<RadioGroup>(R.id.grpDashboardTheme).check(
			if (theme == DashboardTheme.M_MODERN) R.id.rdoThemeModern else R.id.rdoThemeClassic)
		view.findViewById<DashboardView>(R.id.dashboardPreview).theme = theme
		view.findViewById<SwitchCompat>(R.id.swDemoMode).isChecked = appSettings[AppSettings.KEYS.DASHBOARD_DEMO_MODE].toBoolean()

		val list = view.findViewById<LinearLayout>(R.id.listChecks)
		list.removeAllViews()
		diagnostics.checks().forEach { check ->
			val row = layoutInflater.inflate(R.layout.edition_check_item, list, false)
			row.findViewById<TextView>(R.id.txtCheckTitle).text = "${if (check.ok) "✓" else "⚠"} ${check.title}"
			row.findViewById<TextView>(R.id.txtCheckDetail).text = check.detail
			val button = row.findViewById<Button>(R.id.btnCheckAction)
			button.visibility = if (check.action == EditionDiagnostics.Action.NONE) View.GONE else View.VISIBLE
			button.setOnClickListener { openSettings(check.action) }
			list.addView(row)
		}
	}

	private fun openSettings(action: EditionDiagnostics.Action) {
		val packageName = requireContext().packageName
		val intent = when (action) {
			EditionDiagnostics.Action.NOTIFICATION_LISTENER_SETTINGS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
			EditionDiagnostics.Action.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
			EditionDiagnostics.Action.APP_NOTIFICATION_SETTINGS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
			} else {
				Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
			}
			EditionDiagnostics.Action.NONE -> return
		}
		try {
			startActivity(intent)
		} catch (e: ActivityNotFoundException) {
			startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
		}
	}
}
