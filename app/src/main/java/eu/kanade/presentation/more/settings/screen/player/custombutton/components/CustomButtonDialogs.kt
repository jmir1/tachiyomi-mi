package eu.kanade.presentation.more.settings.screen.player.custombutton.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun CustomButtonButtonDialog(
    onDismissRequest: () -> Unit,
    onAction: (String, String, String, String) -> Unit,
    titleRes: StringResource,
    actionRes: StringResource,
    buttonNames: ImmutableList<String>,
    initialState: CustomButton?,
) {
    var title by remember { mutableStateOf(initialState?.name ?: "") }
    var content by remember { mutableStateOf(initialState?.content ?: "") }
    var longPressContent by remember { mutableStateOf(initialState?.longPressContent ?: "") }
    var startUp by remember { mutableStateOf(initialState?.onStartup ?: "") }

    val focusRequester = remember { FocusRequester() }
    val titleAlreadyExists = remember(title) { buttonNames.contains(title) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = title.isNotEmpty() && content.isNotEmpty() && !titleAlreadyExists,
                onClick = {
                    onAction(title, content, longPressContent, startUp)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(actionRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Row {
                Text(text = stringResource(titleRes))
                initialState?.id?.let { buttonId ->
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(MR.strings.pref_player_custom_button_id, buttonId),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.alpha(alpha = DISABLED_ALPHA),
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_title))
                    },
                    supportingText = {
                        val msgRes = if (title.isNotEmpty() && titleAlreadyExists) {
                            MR.strings.pref_player_custom_button_error_exists
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = title.isNotEmpty() && titleAlreadyExists,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_content))
                    },
                    supportingText = {
                        Text(text = stringResource(MR.strings.information_required_plain))
                    },
                    minLines = 3,
                    maxLines = 5,
                )

                OutlinedTextField(
                    value = longPressContent,
                    onValueChange = { longPressContent = it },
                    label = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_content_long))
                    },
                    supportingText = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_optional))
                    },
                    minLines = 3,
                    maxLines = 5,
                )

                OutlinedTextField(
                    value = startUp,
                    onValueChange = { startUp = it },
                    label = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_startup))
                    },
                    supportingText = {
                        Text(text = stringResource(MR.strings.pref_player_custom_button_optional))
                    },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CustomButtonCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
    buttonNames: ImmutableList<String>,
) {
    CustomButtonButtonDialog(
        onDismissRequest = onDismissRequest,
        onAction = onCreate,
        titleRes = MR.strings.pref_player_custom_button_add,
        actionRes = MR.strings.action_add,
        buttonNames = buttonNames,
        initialState = null,
    )
}

@Composable
fun CustomButtonEditDialog(
    onDismissRequest: () -> Unit,
    onEdit: (String, String, String, String) -> Unit,
    buttonNames: ImmutableList<String>,
    initialState: CustomButton,
) {
    CustomButtonButtonDialog(
        onDismissRequest = onDismissRequest,
        onAction = onEdit,
        titleRes = MR.strings.pref_player_custom_button_edit,
        actionRes = MR.strings.action_edit,
        buttonNames = buttonNames,
        initialState = initialState,
    )
}

@Composable
fun CustomButtonDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    buttonTitle: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.pref_player_custom_button_delete))
        },
        text = {
            Text(text = stringResource(MR.strings.pref_player_custom_button_delete_confirm, buttonTitle))
        },
    )
}
