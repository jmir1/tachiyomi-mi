package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.backupCustomButtonsMapper
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CustomButtonBackupCreator(
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupCustomButtons> {
        return getCustomButtons.getAll()
            .map(backupCustomButtonsMapper)
    }
}
