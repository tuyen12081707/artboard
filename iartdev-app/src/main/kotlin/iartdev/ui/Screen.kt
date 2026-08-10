package iartdev.ui

/** Persistent screens reachable from the sidebar. "Run Snapshot" stays a modal wizard — see docs/REDESIGN_PLAN.md §3. */
enum class Screen(val label: String) {
    Gallery("Gallery"),
    ToolPaths("Tool Paths"),
    Help("Help"),
}
