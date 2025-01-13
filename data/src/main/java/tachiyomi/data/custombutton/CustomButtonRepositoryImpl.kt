package tachiyomi.data.custombutton

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.custombuttons.exception.SaveCustomButtonException
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import tachiyomi.mi.data.AnimeDatabase

class CustomButtonRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : CustomButtonRepository {
    override fun subscribeAll(): Flow<List<CustomButton>> {
        return handler.subscribeToList { custom_buttonsQueries.findAll(::mapCustomButton) }
    }

    override suspend fun getAll(): List<CustomButton> {
        return handler.awaitList { custom_buttonsQueries.findAll(::mapCustomButton) }
    }

    override suspend fun insertCustomButton(
        name: String,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    ) {
        try {
            handler.await { custom_buttonsQueries.insert(name, false, sortIndex, content, longPressContent, onStartup) }
        } catch (ex: SQLiteException) {
            throw SaveCustomButtonException(ex)
        }
    }

    override suspend fun updatePartialCustomButton(update: CustomButtonUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialCustomButtons(updates: List<CustomButtonUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    override suspend fun deleteCustomButton(customButtonId: Long) {
        return handler.await { custom_buttonsQueries.delete(customButtonId) }
    }

    private fun AnimeDatabase.updatePartialBlocking(update: CustomButtonUpdate) {
        custom_buttonsQueries.update(
            name = update.name,
            isFavorite = update.isFavorite,
            sortIndex = update.sortIndex,
            content = update.content,
            longPressContent = update.longPressContent,
            customButtonId = update.id,
            onStartup = update.onStartup,
        )
    }

    private fun mapCustomButton(
        id: Long,
        name: String,
        isFavorite: Boolean,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    ): CustomButton = CustomButton(
        id = id,
        name = name,
        isFavorite = isFavorite,
        sortIndex = sortIndex,
        content = content,
        longPressContent = longPressContent,
        onStartup = onStartup,
    )
}
