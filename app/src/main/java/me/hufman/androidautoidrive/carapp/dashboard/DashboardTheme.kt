package me.hufman.androidautoidrive.carapp.dashboard

/** The graphical themes available for the My AI Drive dashboard. */
enum class DashboardTheme(val key: String) {
	M_CLASSIC("M_Classic"),
	M_MODERN("M_Modern");

	/** Theme shown after turning the iDrive controller one notch */
	fun next(): DashboardTheme = values()[(ordinal + 1) % values().size]
	fun previous(): DashboardTheme = values()[(ordinal - 1 + values().size) % values().size]

	companion object {
		fun fromKey(key: String): DashboardTheme = values().firstOrNull { it.key == key } ?: M_CLASSIC
	}
}
