package tachiyomi.domain.custombuttons.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate

interface CustomButtonRepository {

    fun subscribeAll(): Flow<List<CustomButton>>

    suspend fun getAll(): List<CustomButton>

    suspend fun insertCustomButton(
        name: String,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    )

    suspend fun updatePartialCustomButton(update: CustomButtonUpdate)

    suspend fun updatePartialCustomButtons(updates: List<CustomButtonUpdate>)

    suspend fun deleteCustomButton(customButtonId: Long)
}
