package eu.kanade.presentation.more.settings.screen.player.custombutton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.player.custombutton.components.CustomButtonCreateDialog
import eu.kanade.presentation.more.settings.screen.player.custombutton.components.CustomButtonDeleteDialog
import eu.kanade.presentation.more.settings.screen.player.custombutton.components.CustomButtonEditDialog
import eu.kanade.presentation.more.settings.screen.player.custombutton.components.CustomButtonScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.presentation.core.screens.LoadingScreen

object PlayerSettingsCustomButtonScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val screenModel = rememberScreenModel { PlayerSettingsCustomButtonScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is CustomButtonScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CustomButtonScreenState.Success

        CustomButtonScreen(
            state = successState,
            onClickFAQ = { uriHandler.openUri("https://aniyomi.org/docs/guides/player-settings/custom-buttons") },
            onClickCreate = { screenModel.showDialog(CustomButtonDialog.Create) },
            onClickPrimary = { screenModel.togglePrimaryButton(it) },
            onClickEdit = { screenModel.showDialog(CustomButtonDialog.Edit(it)) },
            onClickDelete = { screenModel.showDialog(CustomButtonDialog.Delete(it)) },
            onClickMoveUp = screenModel::moveUp,
            onClickMoveDown = screenModel::moveDown,
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            CustomButtonDialog.Create -> {
                CustomButtonCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = screenModel::createCustomButton,
                    buttonNames = successState.customButtons.fastMap { it.name }.toImmutableList(),
                )
            }
            is CustomButtonDialog.Delete -> {
                CustomButtonDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteCustomButton(dialog.customButton) },
                    buttonTitle = dialog.customButton.name,
                )
            }
            is CustomButtonDialog.Edit -> {
                CustomButtonEditDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onEdit = { title, content, longPressContent, onStartup ->
                        screenModel.editCustomButton(
                            CustomButtonUpdate(
                                id = dialog.customButton.id,
                                name = title,
                                sortIndex = dialog.customButton.sortIndex,
                                content = content,
                                longPressContent = longPressContent,
                                onStartup = onStartup,
                            ),
                        )
                    },
                    buttonNames = (successState.customButtons - dialog.customButton).fastMap {
                        it.name
                    }.toImmutableList(),
                    initialState = dialog.customButton,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is CustomButtonEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
