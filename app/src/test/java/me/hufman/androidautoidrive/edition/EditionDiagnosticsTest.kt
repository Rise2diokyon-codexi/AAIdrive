package me.hufman.androidautoidrive.edition

import me.hufman.androidautoidrive.edition.EditionDiagnostics.Action
import org.junit.Assert.*
import org.junit.Test

class EditionDiagnosticsTest {
	private data class FakeEnvironment(
		override val sdkInt: Int = 34,
		override val packageName: String = "com.myaidrive.claude",
		override val notificationListenerGranted: Boolean = true,
		override val postNotificationsGranted: Boolean = true,
		override val backgroundRestricted: Boolean = false,
		override val installerPackage: String? = "com.android.vending",
		override val spotifyInstalled: Boolean = true,
		override val spotifyClientConfigured: Boolean = true,
		override val companionApps: List<String> = listOf("My BMW"),
		override val otherEditions: List<String> = emptyList(),
		override val carConnectionEnabled: Boolean = true,
		override val genericDebugSignature: Boolean = false,
	) : EditionDiagnostics.Environment

	@Test
	fun healthySetupHasNoWarnings() {
		val checks = EditionDiagnostics(FakeEnvironment()).checks()
		assertTrue(checks.joinToString { "${it.title}: ${it.detail}" }, checks.all { it.ok })
		assertTrue(checks.all { it.action == Action.NONE })
	}

	@Test
	fun sideloadedApkOnAndroid13ExplainsRestrictedSettings() {
		val diagnostics = EditionDiagnostics(FakeEnvironment(notificationListenerGranted = false, installerPackage = "com.google.android.packageinstaller"))
		assertTrue(diagnostics.isSideloaded)
		val check = diagnostics.notificationListenerCheck()
		assertFalse(check.ok)
		assertTrue(check.detail.contains("paramètres restreints"))
		assertEquals(Action.APP_DETAILS, check.action)
	}

	@Test
	fun storeInstallOnlyNeedsTheListenerSetting() {
		val diagnostics = EditionDiagnostics(FakeEnvironment(notificationListenerGranted = false))
		assertFalse(diagnostics.isSideloaded)
		val check = diagnostics.notificationListenerCheck()
		assertFalse(check.detail.contains("paramètres restreints"))
		assertEquals(Action.NOTIFICATION_LISTENER_SETTINGS, check.action)
	}

	@Test
	fun unknownInstallerCountsAsSideloaded() {
		assertTrue(EditionDiagnostics(FakeEnvironment(installerPackage = null)).isSideloaded)
	}

	@Test
	fun olderAndroidHasNoRestrictedSettingsHint() {
		val check = EditionDiagnostics(FakeEnvironment(sdkInt = 31, notificationListenerGranted = false, installerPackage = null)).notificationListenerCheck()
		assertFalse(check.detail.contains("paramètres restreints"))
	}

	@Test
	fun missingSpotifyClientIdExplainsTheRegistration() {
		val diagnostics = EditionDiagnostics(FakeEnvironment(spotifyClientConfigured = false))
		val check = diagnostics.spotifyCheck()
		assertFalse(check.ok)
		assertTrue(check.detail.contains("com.myaidrive.claude://spotify_callback"))
		assertTrue(check.detail.contains("MYAIDRIVE_CLAUDE_SPOTIFY_CLIENT_ID"))
		assertTrue(EditionDiagnostics(FakeEnvironment(spotifyInstalled = false, spotifyClientConfigured = false)).spotifyCheck().ok)
	}

	@Test
	fun otherEditionsMustBeSwitchedOff() {
		val check = EditionDiagnostics(FakeEnvironment(otherEditions = listOf("My AI Drive GPT Edition"))).carConnectionCheck()
		assertFalse(check.ok)
		assertTrue(check.detail.contains("GPT Edition"))
		assertFalse(EditionDiagnostics(FakeEnvironment(carConnectionEnabled = false)).carConnectionCheck().ok)
	}

	@Test
	fun otherProblemsAreReported() {
		val checks = EditionDiagnostics(FakeEnvironment(sdkInt = 34, postNotificationsGranted = false, backgroundRestricted = true,
			companionApps = emptyList(), genericDebugSignature = true)).checks()
		assertEquals(4, checks.count { !it.ok })
		assertTrue(EditionDiagnostics(FakeEnvironment(sdkInt = 30, postNotificationsGranted = false)).postNotificationsCheck().ok)
	}
}
