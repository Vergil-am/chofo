package dev.dogetime.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `POST /oauth2/device`. [deviceCode] is the secret used to poll; never show it. */
@Serializable
data class SimklDeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5,
)

/** Response of `POST /oauth2/token`, for the device-code and refresh grants alike. */
@Serializable
data class SimklToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 604_800,
    val scope: String? = null,
)

@Serializable
data class SimklError(
    val error: String? = null,
    val message: String? = null,
)

/** Response of `GET /users/settings`. Only the fields the app shows. */
@Serializable
data class SimklUserSettings(
    val user: User? = null,
    val account: Account? = null,
) {
    @Serializable
    data class User(val name: String? = null, val avatar: String? = null)

    @Serializable
    data class Account(val id: Long? = null, val type: String? = null)
}

/**
 * Body of `/scrobble/{start,pause,stop}`: [progress] plus exactly one of [movie], [show] or
 * [anime]. Nulls are left out of the JSON.
 */
@Serializable
data class SimklScrobbleBody(
    val progress: Double,
    val movie: SimklMedia? = null,
    val show: SimklMedia? = null,
    val anime: SimklMedia? = null,
    val episode: SimklEpisode? = null,
)

@Serializable
data class SimklMedia(
    val title: String? = null,
    val year: Int? = null,
    val ids: SimklIds,
)

@Serializable
data class SimklIds(
    val tmdb: Int? = null,
    val imdb: String? = null,
    val kitsu: Int? = null,
)

/** [season] is left out for anime, which Simkl numbers by episode alone when given a Kitsu id. */
@Serializable
data class SimklEpisode(
    val season: Int? = null,
    val number: Int,
)

@Serializable
data class SimklScrobbleResult(val action: String? = null)

/** Body of `POST /sync/add-to-list`. Each item carries its own [SimklListItem.to]. */
@Serializable
data class SimklAddToListBody(
    val shows: List<SimklListItem>? = null,
    val anime: List<SimklListItem>? = null,
)

/** [to] is the list: `watching`, `plantowatch`, `hold`, `dropped` or `completed`. */
@Serializable
data class SimklListItem(
    val to: String,
    val title: String? = null,
    val year: Int? = null,
    val ids: SimklIds,
)

/** Response of `GET /sync/activities`. Only the `all` and `playback` timestamps are used. */
@Serializable
data class SimklActivities(
    val movies: Bucket? = null,
    @SerialName("tv_shows") val tvShows: Bucket? = null,
    val anime: Bucket? = null,
) {
    @Serializable
    data class Bucket(val all: String? = null, val playback: String? = null)

    /** Changes whenever a paused session is added, resumed or cleared in any category. */
    val playbackStamp: String
        get() = "${movies?.playback}|${tvShows?.playback}|${anime?.playback}"

    /** Changes whenever anything about the user's shows changes, including watched episodes. */
    val showsStamp: String get() = "${tvShows?.all}"

    /** Same as [showsStamp], for anime. */
    val animeStamp: String get() = "${anime?.all}"
}

/** Response of `GET /sync/all-items/{shows,anime}/watching`. Anime items also sit under `show`. */
@Serializable
data class SimklAllItems(
    val shows: List<SimklListEntry>? = null,
    val anime: List<SimklListEntry>? = null,
)

/** One title on a list. [nextToWatch] is `S01E05` for shows and `E10` for anime. */
@Serializable
data class SimklListEntry(
    @SerialName("next_to_watch") val nextToWatch: String? = null,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("added_to_watchlist_at") val addedAt: String? = null,
    val show: SimklPlaybackMedia? = null,
)

/** One paused session from `GET /sync/playback`. Exactly one of [movie], [show] or [anime] is set. */
@Serializable
data class SimklPlaybackItem(
    val id: Long? = null,
    val progress: Double = 0.0,
    @SerialName("paused_at") val pausedAt: String? = null,
    val type: String? = null,
    val movie: SimklPlaybackMedia? = null,
    val show: SimklPlaybackMedia? = null,
    val anime: SimklPlaybackMedia? = null,
    val episode: SimklPlaybackEpisode? = null,
)

/** Ids are a mix of numbers and strings in Simkl's responses, so they are kept raw. */
@Serializable
data class SimklPlaybackMedia(
    val title: String? = null,
    val year: Int? = null,
    @SerialName("anime_type") val animeType: String? = null,
    val ids: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
) {
    fun intId(name: String): Int? =
        (ids[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
}

@Serializable
data class SimklPlaybackEpisode(
    val season: Int? = null,
    val number: Int? = null,
)
