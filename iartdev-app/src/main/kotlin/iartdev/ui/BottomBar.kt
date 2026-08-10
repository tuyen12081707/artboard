package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import iartdev.theme.IArtDev

/**
 * Centered quick-action bar along the bottom of the window — secondary to the sidebar,
 * for the handful of actions relevant no matter which screen is open. See
 * docs/REDESIGN_PLAN.md §3.
 */
@Composable
fun BottomBar(
    onChangeFolder: () -> Unit,
    onRunSnapshot: () -> Unit,
    onSync: () -> Unit,
    syncEnabled: Boolean,
    syncing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = IArtDev.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.surface),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(label = "Change Folder…", onClick = onChangeFolder)
            when {
                syncing -> DisabledLabel("Syncing…")
                syncEnabled -> SecondaryButton(label = "Sync", onClick = onSync)
                else -> DisabledLabel("Sync (run once first)")
            }
            PrimaryButton(label = "Run Snapshot…", onClick = onRunSnapshot)
        }
    }
}

@Composable
private fun DisabledLabel(label: String) {
    val colors = IArtDev.colors
    androidx.compose.foundation.text.BasicText(
        text = label,
        style = artboard.host.Studio.type.label.copy(color = colors.inkFaint),
    )
}
