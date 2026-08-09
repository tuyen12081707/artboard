package iartdev.prefs

import java.io.File
import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class RecentFolder(val path: String, val openedAtEpochMillis: Long)

/**
 * Last few opened snapshot folders, most-recent-first, persisted as one JSON array
 * in [Preferences]. Replaces the single-folder `lastSnapshotFolder` key from
 * iArtDev's first cut — [migrateLegacyIfNeeded] seeds this list from that key once.
 *
 * Uses the JSON tree API rather than `@Serializable` so this module doesn't need the
 * kotlinx-serialization compiler plugin — matching `iartdev.snapshot.SnapshotManifest`,
 * which makes the same choice for the same reason.
 */
object RecentFolders {
    private const val KEY = "recentFolders"
    private const val LEGACY_KEY = "lastSnapshotFolder"
    private const val MAX_ENTRIES = 5

    fun load(prefs: Preferences): List<RecentFolder> {
        val raw = prefs.get(KEY, null) ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { element ->
                val obj = element.jsonObject
                RecentFolder(
                    path = obj["path"]!!.jsonPrimitive.content,
                    openedAtEpochMillis = obj["openedAtEpochMillis"]!!.jsonPrimitive.content.toLong(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Moves [folder] to the front, de-duplicated by absolute path, capped at [MAX_ENTRIES]. */
    fun recordOpened(prefs: Preferences, folder: File): List<RecentFolder> {
        val path = folder.absolutePath
        val updated = listOf(RecentFolder(path, System.currentTimeMillis())) +
            load(prefs).filterNot { it.path == path }
        return save(prefs, updated.take(MAX_ENTRIES))
    }

    fun remove(prefs: Preferences, path: String): List<RecentFolder> =
        save(prefs, load(prefs).filterNot { it.path == path })

    /** One-time migration from the pre-recent-list single-folder preference. */
    fun migrateLegacyIfNeeded(prefs: Preferences) {
        if (load(prefs).isNotEmpty()) return
        val legacyPath = prefs.get(LEGACY_KEY, null) ?: return
        save(prefs, listOf(RecentFolder(legacyPath, System.currentTimeMillis())))
    }

    private fun save(prefs: Preferences, entries: List<RecentFolder>): List<RecentFolder> {
        val json = buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("path", entry.path)
                        put("openedAtEpochMillis", entry.openedAtEpochMillis)
                    },
                )
            }
        }
        prefs.put(KEY, json.toString())
        return entries
    }
}
