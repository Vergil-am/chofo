package dev.dogetime.domain

/** A single subtitle file available for download. */
data class Subtitle(
    /** Release or file name describing this subtitle. */
    val releaseName: String,
    /** Human-readable language, e.g. "English", "French". */
    val language: String,
    /** Who uploaded this subtitle. */
    val author: String,
    /** Where the subtitle comes from, which decides how [downloadUrl] is downloaded. */
    val provider: SubtitleProvider,
    /**
     * What [provider]'s API needs to download the file: an OpenSubtitles file id, a SubDL CDN
     * path, a YTS Subs page URL, or a Stremio add-on's direct file URL.
     */
    val downloadUrl: String,
    /** Whether this subtitle is for the hearing-impaired (SDH). */
    val hearingImpaired: Boolean = false,
    val season: Int? = null,
    val episode: Int? = null,
)

/** The services subtitles are found on. */
enum class SubtitleProvider {
    OpenSubtitles,
    Subdl,
    YtsSubs,

    /** The Stremio add-ons (OpenSubtitles v3, Anime Kitsu). */
    Stremio,
}

/** Subtitles grouped by language, for display in the UI. */
data class SubtitleGroup(
    val language: String,
    val subtitles: List<Subtitle>,
)
