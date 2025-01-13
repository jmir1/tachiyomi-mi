package tachiyomi.domain.custombuttons.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import java.util.Collections

class ReorderCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    private val mutex = Mutex()

    suspend fun moveUp(customButton: CustomButton): Result =
        await(customButton, MoveTo.UP)

    suspend fun moveDown(customButton: CustomButton): Result =
        await(customButton, MoveTo.DOWN)

    private suspend fun await(customButton: CustomButton, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val customButtons = customButtonRepository.getAll()
                .toMutableList()

            val currentIndex = customButtons.indexOfFirst { it.id == customButton.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            val newPosition = when (moveTo) {
                MoveTo.UP -> currentIndex - 1
                MoveTo.DOWN -> currentIndex + 1
            }.toInt()

            try {
                Collections.swap(customButtons, currentIndex, newPosition)

                val updates = customButtons.mapIndexed { index, customButton ->
                    CustomButtonUpdate(
                        id = customButton.id,
                        sortIndex = index.toLong(),
                    )
                }

                customButtonRepository.updatePartialCustomButtons(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
