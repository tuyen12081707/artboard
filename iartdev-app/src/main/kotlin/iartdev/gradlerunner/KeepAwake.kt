package iartdev.gradlerunner

/**
 * Prevents the machine from sleeping for as long as a snapshot render is running, so a
 * multi-minute Robolectric render (Android snapshot mode) survives the laptop lid
 * closing or the display timing out.
 *
 * Scoped to the render job's own lifecycle — [start] when [GradleSnapshotProcess.run]
 * starts, [stop] when it finishes/fails/is cancelled — rather than for as long as
 * iArtDev is merely open. Running `caffeinate` unconditionally for an idle app would
 * silently fight the user's own power settings for no visible reason.
 *
 * macOS only for now: `caffeinate` is a macOS-specific binary. Windows/Linux have no
 * equivalent wired up yet (see docs/REDESIGN_PLAN.md, Decision D5) — [start] is a no-op
 * there rather than failing.
 */
class KeepAwake {
    @Volatile private var process: Process? = null

    fun start() {
        if (!isMac() || process != null) return
        process = runCatching {
            // -i: prevent idle sleep. Scoped to this process's own lifetime; killing it
            // (in stop()) immediately releases the assertion — no daemon left behind.
            ProcessBuilder("caffeinate", "-i").start()
        }.getOrNull()
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private companion object {
        fun isMac() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    }
}
