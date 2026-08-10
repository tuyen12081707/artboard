package iartdev.prefs

import java.util.prefs.Preferences

/**
 * A local-only display name — not authentication. iArtDev is a single-user desktop
 * tool with no backend to authenticate against; this exists purely so the sidebar can
 * show "Hi, <name>" and a "Log out" action, per the redesign's explicit "for fun is
 * fine" ask. No password, no account, nothing sent over a network.
 *
 * See docs/REDESIGN_PLAN.md, Decision D1.
 */
object Session {
    private const val NAME_KEY = "session.displayName"

    fun loadDisplayName(prefs: Preferences): String? = prefs.get(NAME_KEY, null)?.takeIf { it.isNotBlank() }

    fun signIn(prefs: Preferences, name: String) {
        prefs.put(NAME_KEY, name.trim())
    }

    fun signOut(prefs: Preferences) {
        prefs.remove(NAME_KEY)
    }
}
