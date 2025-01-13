package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CustomButtonRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
) {
    suspend operator fun invoke(
        backupCustomButtons: List<BackupCustomButtons>,
    ) {
        if (backupCustomButtons.isNotEmpty()) {
            val dbCustomButtons = getCustomButtons.getAll()
            val dbCustomButtonsByName = dbCustomButtons.associateBy { it.name }
            var nextSortIndex = dbCustomButtons.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0
            val dbHasFavorite = dbCustomButtons.firstOrNull { it.isFavorite } != null

            backupCustomButtons
                .sortedBy { it.sortIndex }
                .map {
                    val dbCustomButton = dbCustomButtonsByName[it.name]
                    if (dbCustomButton != null) return@map dbCustomButton
                    val sortIndex = nextSortIndex++
                    handler.awaitOneExecutable {
                        val isFavorite = it.isFavorite && !dbHasFavorite
                        custom_buttonsQueries.insert(
                            it.name,
                            isFavorite,
                            sortIndex,
                            it.content,
                            it.longPressContent,
                            it.onStartup,
                        )
                        custom_buttonsQueries.selectLastInsertedRowId()
                    }
                }
        }
    }
}
