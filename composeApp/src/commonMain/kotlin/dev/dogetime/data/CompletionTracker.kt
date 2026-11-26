package dev.dogetime.data

import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.isFinished
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Marks a title Completed on the watchlist (adding it if needed) once it's watched: a movie when
 * it passes [dev.dogetime.domain.FINISHED_FRACTION], a show when the last episode out does.
 * Runs on [appScope], since the save that crosses the line is often the one made on closing.
 */
class CompletionTracker(
    private val watchlist: WatchlistRepository,
    private val nextEpisodes: NextEpisodeFinder,
    private val appScope: CoroutineScope,
) {
    private val done = mutableSetOf<String>()
    private val mutex = Mutex()

    fun onProgress(target: WatchTarget, item: MediaItem, positionMs: Long, durationMs: Long) {
        if (!isFinished(positionMs, durationMs)) return
        appScope.launch {
            val key = "${item.type}:${item.id}:" + when (target) {
                is WatchTarget.Movie -> "-"
                is WatchTarget.Episode -> "${target.season}:${target.episode}"
            }
            // Once per movie or episode while the app runs; later saves past the line change nothing.
            if (!mutex.withLock { done.add(key) }) return@launch
            try {
                val completed = when (target) {
                    is WatchTarget.Movie -> true
                    is WatchTarget.Episode ->
                        nextEpisodes.after(item.type, item.id, target.season, target.episode) == NextEpisode.None
                }
                if (completed) watchlist.set(item, WatchStatus.Completed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline: try again on the next save.
                mutex.withLock { done.remove(key) }
            }
        }
    }
}
