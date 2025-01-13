package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.custombuttons.model.CustomButton

@Serializable
class BackupCustomButtons(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var isFavorite: Boolean,
    @ProtoNumber(3) var sortIndex: Long,
    @ProtoNumber(4) var content: String,
    @ProtoNumber(5) var longPressContent: String,
    @ProtoNumber(6) var onStartup: String,
)

val backupCustomButtonsMapper = { btn: CustomButton ->
    BackupCustomButtons(
        name = btn.name,
        isFavorite = btn.isFavorite,
        sortIndex = btn.sortIndex,
        content = btn.content,
        longPressContent = btn.longPressContent,
        onStartup = btn.onStartup,
    )
}
