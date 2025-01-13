package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SwitchPreference
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.PlayerDialog
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.InputStream

@Composable
fun ScreenshotSheet(
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,

    cachePath: String,
    onSetAsCover: (() -> InputStream) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (String, Boolean) -> InputStream?,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }

    PlayerSheet(
        onDismissRequest,
        modifier,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Photo,
                    onClick = { showSetCoverDialog = true },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onShare { takeScreenshot(cachePath, showSubtitles)!! }
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onSave { takeScreenshot(cachePath, showSubtitles)!! }
                        onDismissRequest()
                    },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    modifier = Modifier.padding(
                        MaterialTheme.padding.medium,
                    ),
                    content = {
                        Text(
                            text = stringResource(MR.strings.screenshot_show_subs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    if (showSetCoverDialog) {
        PlayerDialog(
            title = stringResource(MR.strings.confirm_set_image_as_cover),
            modifier = Modifier.fillMaxWidth(fraction = 0.6F).padding(MaterialTheme.padding.medium),
            onConfirmRequest = {
                onSetAsCover {
                    takeScreenshot(
                        cachePath,
                        showSubtitles,
                    )!!
                }
            },
            onDismissRequest = { showSetCoverDialog = false },
        )
    }
}
