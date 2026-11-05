package me.hufman.androidautoidrive.carapp.dashboard

/** The graphical themes available for the My BMW Drive dashboard. */
enum class DashboardTheme(val key: String) {
	M_CLASSIC("M_Classic"),
	M_MODERN("M_Modern");

	companion object {
		fun fromKey(key: String): DashboardTheme = values().firstOrNull { it.key == key } ?: M_CLASSIC
	}
}
