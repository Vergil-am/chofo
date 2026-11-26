package dev.dogetime.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The stream last played for a movie or episode, saved with its watch progress and synced, so
 * any device can open the same one again instead of picking a source. Field names match the
 * API's `StreamRef`.
 */
@Serializable
data class RememberedStream(
    val kind: Kind,
    /** What to show on the resume button, e.g. "1080p · Torrentio". */
    val label: String? = null,
    @SerialName("addon_id") val addonId: String? = null,
    @SerialName("addon_name") val addonName: String? = null,
    val magnet: String? = null,
    @SerialName("file_idx") val fileIndex: Int? = null,
    @SerialName("file_name") val fileName: String? = null,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** The [VideoSource] name, for [Kind.Web]. */
    val source: String? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("torrent") Torrent,
        @SerialName("direct") Direct,
        @SerialName("web") Web,
    }

    /** The web source to open again, for [Kind.Web]. */
    val videoSource: VideoSource? get() = VideoSource.entries.firstOrNull { it.name == source }

    /** Whether this device has what it needs to open it again. */
    val isPlayable: Boolean
        get() = when (kind) {
            Kind.Torrent -> magnet?.startsWith("magnet:", ignoreCase = true) == true
            Kind.Direct -> url?.startsWith("http") == true
            Kind.Web -> videoSource != null
        }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

        fun decode(text: String?): RememberedStream? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.takeIf { it.isPlayable }

        fun of(option: TorrentOption, addonId: String?): RememberedStream {
            val label = listOfNotNull(option.quality.takeIf { it.isNotBlank() }, option.addonName).joinToString(" · ")
            return if (option.directUrl != null) {
                RememberedStream(
                    kind = Kind.Direct, label = label, addonId = addonId, addonName = option.addonName,
                    url = option.directUrl, headers = option.headers, fileName = option.fileName ?: option.release,
                )
            } else {
                RememberedStream(
                    kind = Kind.Torrent, label = label, addonId = addonId, addonName = option.addonName,
                    magnet = option.magnet, fileIndex = option.fileIndex, fileName = option.fileName,
                )
            }
        }

        fun of(source: VideoSource): RememberedStream = RememberedStream(kind = Kind.Web, label = source.label, source = source.name)
    }
}
