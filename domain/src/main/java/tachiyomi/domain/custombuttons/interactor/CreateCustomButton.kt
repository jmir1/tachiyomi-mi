package tachiyomi.domain.custombuttons.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository

class CreateCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(
        name: String,
        content: String,
        longPressContent: String,
        onStartup: String,
    ): Result = withNonCancellableContext {
        val customButtons = customButtonRepository.getAll()
        val nextSortIndex = customButtons.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0

        try {
            customButtonRepository.insertCustomButton(
                name = name,
                sortIndex = nextSortIndex,
                content = content,
                longPressContent = longPressContent,
                onStartup = onStartup,
            )
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
