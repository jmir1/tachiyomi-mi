package tachiyomi.domain.custombuttons.model

data class CustomButton(
    val id: Long,
    val name: String,
    val isFavorite: Boolean,
    val sortIndex: Long,
    val content: String,
    val longPressContent: String,
    val onStartup: String,
) {
    fun getButtonContent(primaryId: Long): String {
        val isPrimary = if (primaryId == id) "true" else "false"
        return content.replace("${'$'}id", id.toString()).replace("${'$'}isPrimary", isPrimary)
    }

    fun getButtonLongPressContent(primaryId: Long): String {
        val isPrimary = if (primaryId == id) "true" else "false"
        return longPressContent.replace("${'$'}id", id.toString()).replace("${'$'}isPrimary", isPrimary)
    }

    fun getButtonOnStartup(primaryId: Long): String {
        val isPrimary = if (primaryId == id) "true" else "false"
        return onStartup.replace("${'$'}id", id.toString()).replace("${'$'}isPrimary", isPrimary)
    }
}
