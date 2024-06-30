package tachiyomi.domain.category.manga.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import java.util.Collections

class ReorderMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
) {

    private val mutex = Mutex()

    suspend fun moveUp(category: Category): Result =
        await(category, MoveTo.UP)

    suspend fun moveDown(category: Category): Result =
        await(category, MoveTo.DOWN)

    private suspend fun await(category: Category, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAllMangaCategories()
                .filterNot(Category::isSystemCategory)
                .toMutableList()

            val currentIndex = categories.indexOfFirst { it.id == category.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            val newPosition = when (moveTo) {
                MoveTo.UP -> currentIndex - 1
                MoveTo.DOWN -> currentIndex + 1
            }.toInt()

            try {
                Collections.swap(categories, currentIndex, newPosition)

                val updates = categories.mapIndexed { index, category ->
                    CategoryUpdate(
                        id = category.id,
                        order = index.toLong(),
                    )
                }

                categoryRepository.updatePartialMangaCategories(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    suspend fun sortAlphabetically() = withNonCancellableContext {
        mutex.withLock {
            val updates = categoryRepository.getAllMangaCategories()
                .sortedBy { category -> category.name }
                .mapIndexed { index, category ->
                    CategoryUpdate(
                        id = category.id,
                        order = index.toLong(),
                    )
                }

            try {
                categoryRepository.updatePartialMangaCategories(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
