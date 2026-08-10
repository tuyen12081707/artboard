package iartdev.prefs

import java.util.prefs.Preferences

/** The single [Preferences] node every iArtDev prefs helper (recent folders, session, run config) shares. */
fun iartdevPrefs(): Preferences = Preferences.userRoot().node("dev.iartdev")
