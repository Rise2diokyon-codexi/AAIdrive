package me.hufman.androidautoidrive.edition

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.EditionCarConnection
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getApplicationInfoCompat
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class AndroidEditionEnvironment(private val context: Context): EditionDiagnostics.Environment {
	companion object {
		// the companion apps providing the BMW security service, as listed by IDriveConnectKitAndroid's
		// KnownSecurityServices; looked up directly because SecurityAccess only discovers them on connect()
		val COMPANION_APPS = linkedMapOf(
			"com.bmwgroup.connected.bmw.usa" to "BMW Connected Classic (USA)",
			"com.bmwgroup.connected.mini.usa" to "MINI Connected Classic (USA)",
			"com.bmwgroup.connected.bmw" to "BMW Connected Classic",
			"com.bmwgroup.connected.mini" to "MINI Connected Classic",
			"de.bmw.connected.na" to "BMW Connected (NA)",
			"de.mini.connected.na" to "MINI Connected (NA)",
			"de.bmw.connected" to "BMW Connected",
			"de.mini.connected" to "MINI Connected",
		).apply {
			listOf("row", "cn", "hm", "kr", "na").forEach { region ->
				put("de.bmw.connected.mobile20.$region", "My BMW")
				put("de.mini.connected.mobile20.$region", "MINI")
			}
		}
	}

	override val sdkInt: Int = Build.VERSION.SDK_INT
	override val packageName: String = context.packageName

	override val notificationListenerGranted: Boolean
		get() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

	override val postNotificationsGranted: Boolean
		get() = Build.VERSION.SDK_INT < 33 ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

	override val backgroundRestricted: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
			context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

	override val installerPackage: String?
		get() = try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
			} else {
				@Suppress("DEPRECATION")
				context.packageManager.getInstallerPackageName(context.packageName)
			}
		} catch (e: Exception) {
			null
		}

	override val spotifyInstalled: Boolean
		get() = SpotifyAppController.isSpotifyInstalled(context)
	override val spotifyClientConfigured: Boolean
		get() = SpotifyAppController.hasSupport(context)

	override val companionApps: List<String>
		get() = COMPANION_APPS.filterKeys { context.packageManager.getApplicationInfoCompat(it) != null }
			.values.distinct()

	override val otherEditions: List<String>
		get() = EditionCarConnection.installedOtherEditions(context)

	override val carConnectionEnabled: Boolean
		get() = AppSettings[AppSettings.KEYS.CAR_CONNECTION_ENABLED].toBoolean()

	override val genericDebugSignature: Boolean
		get() = signingCertificate()?.subjectX500Principal?.name?.contains("CN=Android Debug") == true

	fun signingCertificate(): X509Certificate? = try {
		val pm = context.packageManager
		val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
				.signingInfo?.apkContentsSigners
		} else {
			@Suppress("DEPRECATION")
			pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
		}
		signatures?.firstOrNull()?.toByteArray()?.let {
			CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(it)) as X509Certificate
		}
	} catch (e: Exception) {
		null
	}

	/** Colon-separated fingerprint, as asked for by the Spotify Developer Dashboard */
	fun signingFingerprint(algorithm: String): String? = signingCertificate()?.let { cert ->
		MessageDigest.getInstance(algorithm).digest(cert.encoded).joinToString(":") { "%02X".format(it) }
	}
}
