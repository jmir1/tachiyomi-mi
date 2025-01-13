package tachiyomi.domain.custombuttons.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository

class UpdateCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(update: CustomButtonUpdate) = withNonCancellableContext {
        try {
            customButtonRepository.updatePartialCustomButton(update)
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    suspend fun await(updates: List<CustomButtonUpdate>) = withNonCancellableContext {
        try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
