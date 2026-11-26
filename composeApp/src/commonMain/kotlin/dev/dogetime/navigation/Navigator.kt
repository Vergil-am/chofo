package dev.dogetime.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
sealed interface Destination {
    @Serializable
    data object Home : Destination
    @Serializable
    data object Browse : Destination
    @Serializable
    data object Search : Destination
    @Serializable
    data object Downloads : Destination
    @Serializable
    data object Settings : Destination
    @Serializable
    data object Watchlist : Destination
    @Serializable
    data class Details(
        val type: MediaType,
        val id: Int,
        val initialItem: MediaItem? = null,
    ) : Destination
    @Serializable
    data class Watch(
        val target: WatchTarget,
        val title: String,
        val backdropUrl: String? = null,
        val mediaItem: MediaItem? = null,
        /** Start the stream last played for [target] straight away, if there is one. */
        val resumeLastStream: Boolean = false,
        /** Play from 0:00 instead of the saved position. */
        val fromBeginning: Boolean = false,
    ) : Destination
    @Serializable
    data class OfflineWatch(
        val item: DownloadItem,
        val fromBeginning: Boolean = false,
    ) : Destination
}

/**
 * Back stack for the app. The first entry is always a bottom-bar tab;
 * details screens are pushed on top of it.
 */
class Navigator(initial: List<Destination> = listOf(Destination.Home)) {
    private val backStack = mutableStateListOf<Destination>().apply { addAll(initial) }

    val current: Destination
        get() = backStack.last()

    val root: Destination
        get() = backStack.first()

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun open(destination: Destination) {
        backStack.add(destination)
    }

    fun back() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }

    fun selectTab(tab: Destination) {
        backStack.clear()
        backStack.add(tab)
    }

    companion object {
        private val backStackSerializer = ListSerializer(Destination.serializer())

        // Not the default "type", which Destination.Details already uses for a field.
        private val json = Json { classDiscriminator = "#destination" }

        /**
         * Saves the back stack as JSON, so Android restores it after killing the app in the
         * background. A stack that can't be read back starts over at Home.
         */
        val Saver: Saver<Navigator, String> = Saver(
            save = { json.encodeToString(backStackSerializer, it.backStack.toList()) },
            restore = { saved ->
                runCatching { json.decodeFromString(backStackSerializer, saved) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::Navigator)
            },
        )
    }
}

/** A [Navigator] whose back stack survives configuration changes and process death. */
@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }
