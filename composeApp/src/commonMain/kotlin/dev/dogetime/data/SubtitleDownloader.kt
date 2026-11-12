package dev.dogetime.data

import dev.dogetime.core.extractAndSaveSubtitleTo
import dev.dogetime.domain.Subtitle

class SubtitleDownloader(
    private val openSubtitles: OpenSubtitlesApi? = null,
    private val subdl: SubdlApi? = null,
    private val ytsSubs: YtsSubsApi? = null,
    private val stremioSubtitles: StremioSubtitlesApi? = null,
) {
    /**
     * Downloads raw subtitle bytes using the appropriate provider for this [subtitle].
     */
    suspend fun downloadBytes(subtitle: Subtitle): ByteArray {
        val url = subtitle.downloadUrl
        return when {
            url.contains("yts-subs.com") -> {
                ytsSubs?.download(url) ?: throw IllegalStateException("YTS Subs provider not available")
            }
            url.contains("strem.io") || url.contains("strem.fun") || (stremioSubtitles != null && url.startsWith("http")) -> {
                stremioSubtitles?.download(url) ?: throw IllegalStateException("Stremio subtitles provider not available")
            }
            openSubtitles != null && (url.toLongOrNull() != null || subdl == null) -> {
                openSubtitles.download(url)
            }
            subdl != null -> {
                subdl.download(url)
            }
            else -> {
                throw IllegalStateException("No subtitle provider available to download: $url")
            }
        }
    }

    /**
     * Downloads the subtitle and saves it alongside the [videoFilePath] with a matching basename
     * e.g. `/path/to/movie.1080p.mp4` -> `/path/to/movie.1080p.en.srt`.
     * Returns the absolute path of the saved subtitle file.
     */
    suspend fun downloadAndSaveSidecar(
        videoFilePath: String,
        subtitle: Subtitle,
    ): String? {
        val bytes = downloadBytes(subtitle)
        val extIndex = videoFilePath.lastIndexOf('.')
        val basePath = if (extIndex != -1) videoFilePath.substring(0, extIndex) else videoFilePath
        val langCode = subtitle.language.take(2).lowercase()
        val sidecarPath = "$basePath.$langCode.srt"
        return extractAndSaveSubtitleTo(bytes, subtitle.releaseName, sidecarPath)
    }
}
