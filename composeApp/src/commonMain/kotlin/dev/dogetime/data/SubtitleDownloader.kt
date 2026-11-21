package dev.dogetime.data

import dev.dogetime.core.extractAndSaveSubtitleTo
import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.SubtitleProvider

class SubtitleDownloader(
    private val openSubtitles: OpenSubtitlesApi,
    private val subdl: SubdlApi,
    private val ytsSubs: YtsSubsApi,
    private val stremioSubtitles: StremioSubtitlesApi,
) {
    /** Downloads [subtitle]'s raw bytes (often a zip) from the provider it was found on. */
    suspend fun downloadBytes(subtitle: Subtitle): ByteArray {
        val url = subtitle.downloadUrl
        return when (subtitle.provider) {
            SubtitleProvider.OpenSubtitles -> openSubtitles.download(url)
            SubtitleProvider.Subdl -> subdl.download(url)
            SubtitleProvider.YtsSubs -> ytsSubs.download(url)
            SubtitleProvider.Stremio -> stremioSubtitles.download(url)
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
