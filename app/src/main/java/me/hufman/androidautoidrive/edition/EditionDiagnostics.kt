package me.hufman.androidautoidrive.edition

/**
 * Pure diagnosis of the Android-side conditions the car integration depends on.
 * The Android lookups live in [AndroidEditionEnvironment] so this logic stays unit-testable.
 */
class EditionDiagnostics(private val env: Environment) {
	interface Environment {
		val sdkInt: Int
		val packageName: String
		val notificationListenerGranted: Boolean
		val postNotificationsGranted: Boolean
		val backgroundRestricted: Boolean
		/** Package that installed this APK, null when unknown (e.g. adb or older Android) */
		val installerPackage: String?
		val spotifyInstalled: Boolean
		val spotifyClientConfigured: Boolean
		val companionApps: List<String>
		val otherEditions: List<String>
		val carConnectionEnabled: Boolean
		/** True when signed with an auto-generated "CN=Android Debug" key, which changes on every CI build */
		val genericDebugSignature: Boolean
	}

	enum class Action { NONE, NOTIFICATION_LISTENER_SETTINGS, APP_DETAILS, APP_NOTIFICATION_SETTINGS }

	data class Check(val title: String, val ok: Boolean, val detail: String, val action: Action = Action.NONE)

	companion object {
		const val SDK_TIRAMISU = 33
		val STORE_INSTALLERS = setOf(
			"com.android.vending",                  // Google Play
			"org.fdroid.fdroid",
			"com.aurora.store",
			"com.sec.android.app.samsungapps",
			"com.huawei.appmarket",
			"com.amazon.venezia",
		)
	}

	/** Installed outside an app store, which is when Android 13+ restricts Notification Access */
	val isSideloaded: Boolean
		get() = env.installerPackage?.let { it in STORE_INSTALLERS } != true

	val spotifyRedirectUri: String
		get() = "${env.packageName}://spotify_callback"

	fun checks(): List<Check> = listOf(
		carConnectionCheck(),
		notificationListenerCheck(),
		postNotificationsCheck(),
		backgroundCheck(),
		spotifyCheck(),
		companionAppCheck(),
		signatureCheck(),
	)

	fun carConnectionCheck(): Check {
		val others = env.otherEditions
		return when {
			!env.carConnectionEnabled -> Check("Connexion voiture de cette édition", false,
				"Désactivée : cette édition ne se connectera pas à la voiture. Activez-la ici si c'est l'édition à tester.")
			others.isNotEmpty() -> Check("Connexion voiture de cette édition", false,
				"Activée, mais d'autres éditions sont installées (${others.joinToString()}). " +
				"Désactivez leur connexion voiture pour qu'une seule édition parle à iDrive.")
			else -> Check("Connexion voiture de cette édition", true, "Activée, aucune autre édition détectée.")
		}
	}

	fun notificationListenerCheck(): Check {
		if (env.notificationListenerGranted) {
			return Check("Accès aux notifications", true,
				"Accordé : sessions multimédia (titres, pochettes, commandes) et notifications disponibles.")
		}
		val restrictedHint = if (env.sdkInt >= SDK_TIRAMISU && isSideloaded) {
			" Android 13+ grise ce réglage pour les APK installés hors store : ouvrez Infos de l'appli → ⋮ → " +
			"« Autoriser les paramètres restreints », puis activez l'accès aux notifications."
		} else ""
		return Check("Accès aux notifications", false,
			"Non accordé : sans lui, l'app ne voit aucune session multimédia, les applis musicales " +
			"apparaissent grisées et la liste iDrive reste vide.$restrictedHint",
			if (restrictedHint.isNotEmpty()) Action.APP_DETAILS else Action.NOTIFICATION_LISTENER_SETTINGS)
	}

	fun postNotificationsCheck(): Check {
		val ok = env.sdkInt < SDK_TIRAMISU || env.postNotificationsGranted
		return Check("Notifications de l'application", ok,
			if (ok) "Autorisées (notification de connexion visible)."
			else "Refusées : la notification de service n'est pas affichée, Android peut arrêter la connexion plus tôt.",
			if (ok) Action.NONE else Action.APP_NOTIFICATION_SETTINGS)
	}

	fun backgroundCheck(): Check {
		val ok = !env.backgroundRestricted
		return Check("Fonctionnement en arrière-plan", ok,
			if (ok) "Non restreint." else "Restreint par l'optimisation batterie : la connexion voiture peut être coupée.",
			if (ok) Action.NONE else Action.APP_DETAILS)
	}

	fun spotifyCheck(): Check = when {
		!env.spotifyInstalled -> Check("Spotify", true, "Spotify n'est pas installé.")
		env.spotifyClientConfigured -> Check("Spotify", true,
			"Client ID configuré. Il doit être enregistré dans le Spotify Developer Dashboard avec le package " +
			"${env.packageName}, l'empreinte SHA-1 ci-dessus et l'URI de redirection $spotifyRedirectUri.")
		else -> Check("Spotify", false,
			"Aucun Client ID Spotify dans ce build : Spotify peut jouer, mais titres, pochettes, playlists et " +
			"navigation iDrive ne sont pas disponibles. Créez une app Spotify (package ${env.packageName}, " +
			"redirection $spotifyRedirectUri) et fournissez son Client ID au build via le secret GitHub " +
			"MYAIDRIVE_CLAUDE_SPOTIFY_CLIENT_ID.")
	}

	fun companionAppCheck(): Check {
		val apps = env.companionApps
		return if (apps.isNotEmpty()) Check("Application BMW/MINI compagnon", true, "Trouvée : ${apps.joinToString()}.")
		else Check("Application BMW/MINI compagnon", false,
			"Aucune trouvée : la connexion iDrive passe par l'app BMW Connected / My BMW installée sur le téléphone.")
	}

	fun signatureCheck(): Check = if (env.genericDebugSignature) {
		Check("Signature de l'APK", false,
			"Clé de debug générée automatiquement : elle change à chaque build CI, la mise à jour impose de " +
			"désinstaller et Spotify ne peut pas reconnaître l'app. Configurez les secrets MYAIDRIVE_KEYSTORE_* " +
			"(voir docs/claude-edition).")
	} else {
		Check("Signature de l'APK", true, "Clé de signature stable propre à l'édition.")
	}
}
