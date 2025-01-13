/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding

@Composable
fun BottomRightPlayerControls(
    customButton: CustomButton?,
    customButtonTitle: String,
    aniskipButton: String?,
    onPressAniSkipButton: () -> Unit,
    isPipAvailable: Boolean,
    onAspectClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        if (aniskipButton != null) {
            Box(
                modifier = Modifier.padding(end = MaterialTheme.padding.small),
            ) {
                Button(onClick = {}) {
                    Text(text = aniskipButton)
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .combinedClickable(
                            onClick = onPressAniSkipButton,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                )
            }
        } else if (customButton != null) {
            Box(
                modifier = Modifier.padding(end = MaterialTheme.padding.small),
            ) {
                Button(onClick = {}) {
                    Text(text = customButtonTitle)
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .combinedClickable(
                            onClick = { customButton.execute() },
                            onLongClick = { customButton.executeLongPress() },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                )
            }
        }

        if (isPipAvailable) {
            ControlsButton(
                Icons.Default.PictureInPictureAlt,
                onClick = onPipClick,
            )
        }

        ControlsButton(
            Icons.Default.AspectRatio,
            onClick = onAspectClick,
        )
    }
}
