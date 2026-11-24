package dev.dogetime.data

import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong
import kotlin.time.Clock

/** The signed-in Simkl account, as cached on this device. */
data class SimklAccount(
    val username: String,
    /** `free`, `pro` or `vip`, when known. */
    val accountType: String?,
    /** From the last response's rate-limit headers; null until Simkl has sent them. */
    val requestsLeftToday: Int?,
    val dailyLimit: Int?,
)

sealed interface SimklLoginResult {
    data object Success : SimklLoginResult
    data object Expired : SimklLoginResult
    data class Failed(val message: String) : SimklLoginResult
}

class SimklException(message: String) : Exception(message)

/**
 * Simkl sign-in and scrobbling.
 *
 * Free accounts get about 500 requests a day, shared by every app the user connected, so this
 * spends as few as it can:
 * - The profile is fetched once, at sign-in, and cached. Nothing is fetched at app start.
 * - The access token is refreshed only when it is about to expire (it lasts 7 days), or once
 *   after a 401.
 * - Playback events wait [settleMs] (5 s) before being sent, so a quick pause/resume, a seek or a
 *   player closed within seconds costs nothing. Only changes are sent: a 45-minute episode
 *   usually costs two requests (start, stop).
 * - Items Simkl can't match are remembered and never sent again.
 * - A show goes on the Watching list once, after its first accepted `start` on this device.
 * - The quota headers are saved, and when the daily quota runs out nothing is sent until
 *   Simkl's `Retry-After` has passed.
 * - Writes go one at a time, at most one per second, as Simkl requires.
 * - Paused sessions and the Watching lists are fetched only when `/sync/activities` says they
 *   changed, and that check
 *   runs at most every 15 minutes.
 */
class SimklRepository(
    private val api: SimklApi,
    private val prefs: PreferenceDao,
    private val appScope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val settleMs: Long = SETTLE_MS,
    private val minWriteSpacingMs: Long = MIN_WRITE_SPACING_MS,
) : Scrobbler {

    val isConfigured: Boolean get() = api.isConfigured

    private val _account = MutableStateFlow<SimklAccount?>(null)
    val account: StateFlow<SimklAccount?> = _account.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private var session: StoredSession? = null
    private var quota = StoredQuota()
    private val unmatched = LinkedHashSet<String>()

    /** Shows already put on the Watching list from this device, so each costs one request. */
    private val listed = LinkedHashSet<String>()

    /** Guards [session] and token refreshes, so only one refresh runs (a refresh invalidates the old token). */
    private val authMutex = Mutex()

    /** Simkl asks for one request in flight per user. */
    private val requestMutex = Mutex()
    private var lastWriteAt = 0L

    init {
        appScope.launch {
            try {
                session = read(KEY_SESSION)?.let { json.decodeFromString<StoredSession>(it) }
                quota = read(KEY_QUOTA)?.let { json.decodeFromString<StoredQuota>(it) } ?: StoredQuota()
                read(KEY_UNMATCHED)?.let { unmatched += json.decodeFromString<List<String>>(it) }
                read(KEY_LISTED)?.let { listed += json.decodeFromString<List<String>>(it) }
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Loading the Simkl session failed: ${e.message}")
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    // ---- Sign-in ----

    /** Starts the device sign-in. Show [SimklDeviceCode.userCode] and the verification link. */
    suspend fun startLogin(): SimklDeviceCode {
        val response = api.requestDeviceCode()
        return response.body ?: throw SimklException(
            when (response.status) {
                401 -> "Simkl rejected this app's client id."
                else -> "Simkl didn't start the sign-in (${response.error ?: "HTTP ${response.status}"})."
            },
        )
    }

    /** Polls until the user approves [code] on simkl.com, the code expires, or this is cancelled. */
    suspend fun awaitLogin(code: SimklDeviceCode): SimklLoginResult {
        var intervalMs = code.interval.coerceAtLeast(1) * 1000L
        val deadline = now() + code.expiresIn * 1000L
        while (now() < deadline) {
            delay(intervalMs)
            val response = try {
                api.pollDeviceToken(code.deviceCode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue // A dropped connection; the next poll tries again.
            }
            val token = response.body
            when {
                response.isSuccess && token != null -> {
                    completeLogin(token)
                    return SimklLoginResult.Success
                }
                response.error == "authorization_pending" -> Unit
                response.error == "slow_down" -> intervalMs += 5_000
                response.error == "expired_token" -> return SimklLoginResult.Expired
                else -> return SimklLoginResult.Failed(response.error ?: "HTTP ${response.status}")
            }
        }
        return SimklLoginResult.Expired
    }

    private suspend fun completeLogin(token: SimklToken) {
        // The only profile request: its name is cached and shown from then on.
        val settings = try {
            api.userSettings(token.accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        authMutex.withLock {
            session = StoredSession(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtMs = now() + token.expiresIn * 1000,
                username = settings?.body?.user?.name?.takeIf { it.isNotBlank() } ?: "Simkl user",
                accountType = settings?.body?.account?.type,
            )
            saveSession()
        }
        settings?.let { recordQuota(it) }
        publish()
    }

    /** Signs out on this device right away, then revokes the token on Simkl's side. */
    fun logout() {
        val old = session ?: return
        _account.value = null
        appScope.launch {
            authMutex.withLock { clearLocalData() }
            try {
                api.revoke(old.refreshToken ?: old.accessToken)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The local sign-out already happened; the user can also revoke on simkl.com.
            }
        }
    }

    /** Clears the cached session and per-account caches, as Simkl's rules ask on sign-out. */
    private suspend fun clearLocalData() {
        session = null
        quota = StoredQuota()
        unmatched.clear()
        listed.clear()
        listOf(KEY_SESSION, KEY_QUOTA, KEY_UNMATCHED, KEY_LISTED, KEY_PLAYBACK_STAMP, KEY_SHOWS_STAMP, KEY_ANIME_STAMP, KEY_ACTIVITY_CHECKED_AT).forEach { runCatchingIo { prefs.delete(it) } }
        publish()
    }

    // ---- Scrobbling ----

    override fun newSession(target: WatchTarget, item: MediaItem): ScrobbleSession? {
        if (!api.isConfigured) return null
        val base = scrobbleBody(target, item, 0.0) ?: return null
        val listing = (target as? WatchTarget.Episode)?.let { watchingListBody(base) }
        return SimklSession(matchKey(target, item), "${item.type.name}:${item.id}", listing) { progress ->
            scrobbleBody(target, item, progress)!!
        }
    }

    private inner class SimklSession(
        private val key: String,
        /** The show's key for [listed]; [listing] is null for movies. */
        private val showKey: String,
        private val listing: SimklAddToListBody?,
        private val body: (Double) -> SimklScrobbleBody,
    ) : ScrobbleSession {
        private val events = Channel<Event>(Channel.UNLIMITED)

        init {
            appScope.launch { run() }
        }

        override fun onProgress(percent: Double) {
            events.trySend(Event.Progress(percent))
        }

        override fun onPlayingChanged(isPlaying: Boolean, percent: Double) {
            events.trySend(Event.Playing(isPlaying, percent))
        }

        override fun onFinished(percent: Double) {
            events.trySend(Event.Finished(percent))
        }

        private suspend fun run() {
            var progress = 0.0
            var wantPlaying = false
            var settleAt: Long? = null
            var sent: ScrobbleAction? = null
            while (true) {
                val due = settleAt
                val event = if (due == null) {
                    events.receive()
                } else {
                    withTimeoutOrNull((due - now()).coerceAtLeast(0)) { events.receive() }
                }
                when (event) {
                    null -> {
                        // Playing or paused for settleMs: send it if it changed anything.
                        settleAt = null
                        val action = if (wantPlaying) ScrobbleAction.Start else ScrobbleAction.Pause
                        val worthSending = action != sent && !(action == ScrobbleAction.Pause && sent == null)
                        if (worthSending && send(action, key, body(progress))) {
                            sent = action
                            if (action == ScrobbleAction.Start && listing != null) addToWatching(showKey, listing)
                        }
                    }
                    is Event.Progress -> progress = event.percent.clampPercent()
                    is Event.Playing -> {
                        progress = event.percent.clampPercent()
                        wantPlaying = event.isPlaying
                        settleAt = now() + settleMs
                    }
                    is Event.Finished -> {
                        events.close()
                        progress = maxOf(progress, event.percent.clampPercent())
                        // Nothing was started and it wasn't watched: Simkl has nothing to record.
                        if (sent != null || progress >= WATCHED_PERCENT) {
                            send(ScrobbleAction.Stop, key, body(progress))
                        }
                        return
                    }
                }
            }
        }
    }

    private sealed interface Event {
        data class Progress(val percent: Double) : Event
        data class Playing(val isPlaying: Boolean, val percent: Double) : Event
        data class Finished(val percent: Double) : Event
    }

    /** Sends one scrobble. True when Simkl accepted it. */
    private suspend fun send(action: ScrobbleAction, key: String, body: SimklScrobbleBody): Boolean {
        loaded.await()
        if (key in unmatched) return false
        val response = authorized("scrobble/${action.path}", isWrite = true) { token ->
            api.scrobble(action, token, body)
        } ?: return false
        return when {
            // 409: this was already scrobbled in the last hour.
            response.isSuccess || response.status == 409 -> true
            response.status == 404 -> {
                rememberUnmatched(key)
                false
            }
            else -> false
        }
    }

    /** Puts the show on the Watching list, unless this device already did. */
    private suspend fun addToWatching(showKey: String, body: SimklAddToListBody) {
        if (showKey in listed) return
        val response = authorized("sync/add-to-list", isWrite = true) { token -> api.addToList(token, body) }
            ?: return // Not sent (offline, out of quota): tried again on the next episode.
        // A 400 means the body itself is wrong, so retrying would only waste requests.
        if (response.isSuccess || response.status == 400) {
            listed += showKey
            while (listed.size > MAX_LISTED) listed.remove(listed.first())
            runCatchingIo { prefs.upsert(PreferenceEntity(KEY_LISTED, json.encodeToString(listed.toList()))) }
        }
    }

    /**
     * Runs one user-authenticated request: one at a time (Simkl asks for one request in flight
     * per user), with writes spaced a second apart, the quota recorded, one token refresh after
     * a 401, and short retries for Simkl's write lock, bursts and server errors. Null when it
     * wasn't sent (signed out, out of quota, offline); otherwise the final response.
     */
    private suspend fun <T> authorized(
        name: String,
        isWrite: Boolean,
        call: suspend (accessToken: String) -> SimklResponse<T>,
    ): SimklResponse<T>? = requestMutex.withLock {
        loaded.await()
        var token = accessToken() ?: return null
        var refreshed = false
        var lockRetries = 0
        var otherRetries = 0
        while (true) {
            if (now() < quota.blockedUntilMs) return null
            if (isWrite) {
                val wait = lastWriteAt + minWriteSpacingMs - now()
                if (wait > 0) delay(wait)
                lastWriteAt = now()
            }
            val response = try {
                call(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Simkl $name failed: ${e.message}")
                return null
            }
            recordQuota(response)
            when {
                response.status == 401 && !refreshed -> {
                    refreshed = true
                    token = accessToken(forceRefresh = true) ?: return null
                }
                // Another write for this user (e.g. from another app) holds Simkl's lock.
                isWrite && response.status == 400 && response.error.equals("RATE_LIMIT", ignoreCase = true) &&
                    lockRetries < LOCK_RETRY_DELAYS_MS.size -> delay(LOCK_RETRY_DELAYS_MS[lockRetries++])
                (response.status == 429 && response.error == "rate_limit" || response.status >= 500) &&
                    otherRetries < 2 -> delay(2_000L * ++otherRetries)
                else -> {
                    if (!response.isSuccess) println("Simkl $name rejected: ${response.status} ${response.error}")
                    return response
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    // ---- Paused sessions ----

    /**
     * What changed on Simkl since the last call: paused sessions and the Watching lists of shows
     * and anime, each only when `/sync/activities` says it changed. Costs that one request when
     * nothing changed, one more per changed part, and none when called again within
     * [ACTIVITY_CHECK_INTERVAL_MS]. Null when there's nothing new.
     */
    suspend fun changesSinceLastCheck(): SimklChanges? {
        loaded.await()
        if (session == null) return null
        val lastCheck = read(KEY_ACTIVITY_CHECKED_AT)?.toLongOrNull() ?: 0L
        if (now() - lastCheck < ACTIVITY_CHECK_INTERVAL_MS) return null
        val activities = authorized("sync/activities", isWrite = false) { api.activities(it) }
            ?.takeIf { it.isSuccess }?.body ?: return null
        runCatchingIo { prefs.upsert(PreferenceEntity(KEY_ACTIVITY_CHECKED_AT, now().toString())) }
        val changes = SimklChanges(
            playback = ifChanged(KEY_PLAYBACK_STAMP, activities.playbackStamp, "sync/playback") { api.playback(it) },
            watchingShows = ifChanged(KEY_SHOWS_STAMP, activities.showsStamp, "sync/all-items/shows") {
                api.allItems(it, "shows", "watching")
            }?.let { it.shows.orEmpty() },
            watchingAnime = ifChanged(KEY_ANIME_STAMP, activities.animeStamp, "sync/all-items/anime") {
                api.allItems(it, "anime", "watching")
            }?.let { it.anime.orEmpty() },
        )
        return changes.takeIf { it.playback != null || it.watchingShows != null || it.watchingAnime != null }
    }

    /**
     * Fetches with [call] when [stamp] differs from the one saved under [key]. The stamp is saved
     * only once the response is in hand, so a failed fetch is retried next time. An empty list
     * comes back as an empty body, hence the [empty] fallback.
     */
    private suspend inline fun <reified T> ifChanged(
        key: String,
        stamp: String,
        name: String,
        noinline call: suspend (accessToken: String) -> SimklResponse<T>,
    ): T? {
        if (stamp == read(key)) return null
        val response = authorized(name, isWrite = false, call)?.takeIf { it.isSuccess } ?: return null
        val body = response.body ?: empty<T>() ?: return null
        runCatchingIo { prefs.upsert(PreferenceEntity(key, stamp)) }
        return body
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> empty(): T? = when (T::class) {
        List::class -> emptyList<Any>() as T
        SimklAllItems::class -> SimklAllItems() as T
        else -> null
    }

    // ---- Tokens ----

    /** A usable access token, refreshing it first when it expires within [REFRESH_MARGIN_MS]. */
    private suspend fun accessToken(forceRefresh: Boolean = false): String? = authMutex.withLock {
        val current = session ?: return null
        val stillValid = now() < current.expiresAtMs
        if (!forceRefresh && now() < current.expiresAtMs - REFRESH_MARGIN_MS) return current.accessToken
        val refreshToken = current.refreshToken ?: return current.accessToken.takeIf { stillValid }
        val response = try {
            api.refreshToken(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return current.accessToken.takeIf { stillValid }
        }
        val token = response.body
        if (response.isSuccess && token != null) {
            val updated = current.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken ?: refreshToken,
                expiresAtMs = now() + token.expiresIn * 1000,
            )
            session = updated
            saveSession()
            return updated.accessToken
        }
        if (response.status == 400 || response.status == 401) {
            // The grant was revoked (e.g. from simkl.com): sign out here too.
            clearLocalData()
            return null
        }
        current.accessToken.takeIf { stillValid }
    }

    // ---- Quota ----

    private suspend fun recordQuota(response: SimklResponse<*>) {
        var updated = quota.copy(
            remaining = response.remaining ?: quota.remaining,
            limit = response.limit ?: quota.limit,
        )
        updated = when {
            response.status == 429 && response.error == "user_limit_exceeded" -> updated.copy(
                remaining = 0,
                blockedUntilMs = now() + (response.retryAfterSeconds ?: 3_600) * 1000,
            )
            // Simkl's throttling block after too many writes.
            response.status == 412 -> updated.copy(blockedUntilMs = now() + THROTTLE_BLOCK_MS)
            // The last request of the day went through. Wait before trying again; the next
            // rejection carries the exact reset time.
            response.remaining == 0 -> updated.copy(blockedUntilMs = now() + EMPTY_QUOTA_WAIT_MS)
            else -> updated
        }
        if (updated != quota) {
            quota = updated
            runCatchingIo { prefs.upsert(PreferenceEntity(KEY_QUOTA, json.encodeToString(updated))) }
            publish()
        }
    }

    private suspend fun rememberUnmatched(key: String) {
        unmatched += key
        while (unmatched.size > MAX_UNMATCHED) unmatched.remove(unmatched.first())
        runCatchingIo { prefs.upsert(PreferenceEntity(KEY_UNMATCHED, json.encodeToString(unmatched.toList()))) }
    }

    // ---- Storage ----

    private fun publish() {
        _account.value = session?.let {
            SimklAccount(it.username, it.accountType, quota.remaining, quota.limit)
        }
    }

    private suspend fun saveSession() {
        val current = session ?: return
        runCatchingIo { prefs.upsert(PreferenceEntity(KEY_SESSION, json.encodeToString(current))) }
    }

    private suspend fun read(key: String): String? = prefs.observe(key).first()

    private suspend fun runCatchingIo(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Saving Simkl data failed: ${e.message}")
        }
    }

    @Serializable
    private data class StoredSession(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long,
        val username: String,
        val accountType: String?,
    )

    @Serializable
    private data class StoredQuota(
        val remaining: Int? = null,
        val limit: Int? = null,
        val blockedUntilMs: Long = 0,
    )

    companion object {
        /** How long playing or paused must last before it's sent. */
        const val SETTLE_MS = 5_000L

        /** Simkl marks an item watched when stopped at or past this. */
        const val WATCHED_PERCENT = 80.0

        private const val MIN_WRITE_SPACING_MS = 1_100L
        private val LOCK_RETRY_DELAYS_MS = longArrayOf(5_000, 15_000)
        private const val REFRESH_MARGIN_MS = 24 * 60 * 60 * 1000L
        private const val THROTTLE_BLOCK_MS = 10 * 60 * 1000L
        private const val EMPTY_QUOTA_WAIT_MS = 60 * 60 * 1000L
        private const val MAX_UNMATCHED = 500
        private const val MAX_LISTED = 1_000

        private const val KEY_SESSION = "simkl_session"
        private const val KEY_QUOTA = "simkl_quota"
        private const val KEY_UNMATCHED = "simkl_unmatched"
        private const val KEY_LISTED = "simkl_listed"
        private const val KEY_PLAYBACK_STAMP = "simkl_playback_stamp"
        private const val KEY_SHOWS_STAMP = "simkl_shows_stamp"
        private const val KEY_ANIME_STAMP = "simkl_anime_stamp"
        private const val KEY_ACTIVITY_CHECKED_AT = "simkl_activity_checked_at"

        /** How often Continue Watching may ask Simkl whether anything changed. */
        const val ACTIVITY_CHECK_INTERVAL_MS = 15 * 60 * 1000L

        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun Double.clampPercent(): Double = (coerceIn(0.0, 100.0) * 100).roundToLong() / 100.0

/** The `add-to-list` body that puts [scrobble]'s show or anime on the Watching list. */
internal fun watchingListBody(scrobble: SimklScrobbleBody): SimklAddToListBody? {
    fun SimklMedia.toWatching() = SimklListItem(to = "watching", title = title, year = year, ids = ids)
    return scrobble.show?.let { SimklAddToListBody(shows = listOf(it.toWatching())) }
        ?: scrobble.anime?.let { SimklAddToListBody(anime = listOf(it.toWatching())) }
}

/** What [SimklRepository.changesSinceLastCheck] found. A null part didn't change. */
class SimklChanges(
    val playback: List<SimklPlaybackItem>?,
    val watchingShows: List<SimklListEntry>?,
    val watchingAnime: List<SimklListEntry>?,
)

/** One key per movie or episode, for the "Simkl can't match this" cache. */
internal fun matchKey(target: WatchTarget, item: MediaItem): String = when (target) {
    is WatchTarget.Movie -> "${item.type.name}:${item.id}"
    is WatchTarget.Episode -> "${item.type.name}:${item.id}:${target.season}:${target.episode}"
}

/**
 * The scrobble body for [target]. Movies and TV use TMDB ids (plus IMDb when known). Anime uses
 * its Kitsu id with the plain Kitsu episode number, which Simkl matches without a season.
 * Null when the target and item don't fit together.
 */
internal fun scrobbleBody(target: WatchTarget, item: MediaItem, progress: Double): SimklScrobbleBody? {
    val year = item.year?.take(4)?.toIntOrNull()
    return when (item.type) {
        MediaType.Anime -> {
            val anime = SimklMedia(item.title, year, SimklIds(kitsu = target.kitsuId ?: item.id))
            when (target) {
                is WatchTarget.Movie -> SimklScrobbleBody(progress, anime = anime)
                is WatchTarget.Episode -> SimklScrobbleBody(
                    progress,
                    anime = anime,
                    episode = SimklEpisode(number = target.episode),
                )
            }
        }
        MediaType.Movie -> {
            val movie = target as? WatchTarget.Movie ?: return null
            SimklScrobbleBody(progress, movie = SimklMedia(item.title, year, SimklIds(tmdb = movie.id, imdb = movie.imdbId)))
        }
        MediaType.Tv -> {
            val episode = target as? WatchTarget.Episode ?: return null
            SimklScrobbleBody(
                progress,
                show = SimklMedia(item.title, year, SimklIds(tmdb = episode.showId, imdb = episode.imdbId)),
                episode = SimklEpisode(season = episode.season, number = episode.episode),
            )
        }
    }
}
