package dev.dogetime.data

import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** App preferences that are kept between launches. */
class PreferencesRepository(
    private val dao: PreferenceDao,
    private val appScope: CoroutineScope,
) {
    /** The id of the selected theme, or null if none was ever picked. */
    fun observeThemeId(): Flow<String?> = dao.observe(KEY_THEME)

    /** Saves the selected theme. Runs on the app scope, so it finishes even if the screen closes. */
    fun setThemeId(id: String) {
        appScope.launch {
            try {
                dao.upsert(PreferenceEntity(KEY_THEME, id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Saving the theme failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
