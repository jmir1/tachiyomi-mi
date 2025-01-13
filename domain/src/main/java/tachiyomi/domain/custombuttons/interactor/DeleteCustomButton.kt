package tachiyomi.domain.custombuttons.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository

class DeleteCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(customButtonId: Long) = withNonCancellableContext {
        try {
            customButtonRepository.deleteCustomButton(customButtonId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val customButtons = customButtonRepository.getAll()
        val updates = customButtons.mapIndexed { index, customButton ->
            CustomButtonUpdate(
                id = customButton.id,
                sortIndex = index.toLong(),
            )
        }

        try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
