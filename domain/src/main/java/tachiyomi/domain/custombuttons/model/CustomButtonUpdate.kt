package tachiyomi.domain.custombuttons.model

data class CustomButtonUpdate(
    val id: Long,
    val name: String? = null,
    val isFavorite: Boolean? = null,
    val sortIndex: Long? = null,
    val content: String? = null,
    val longPressContent: String? = null,
    val onStartup: String? = null,
)
