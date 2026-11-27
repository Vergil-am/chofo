package dev.dogetime.core

/** A file in a torrent: its path inside the torrent and its size in bytes. */
internal data class TorrentFileEntry(val path: String, val size: Long)

internal val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "wmv", "flv")

/**
 * The index in [files] of the video to play, or null when there is no video file. In order:
 * the file matching [season] and [episode], the file named [fileName], [fileIndex] if it is a
 * video, the [episode]-th video by name, and finally the largest video.
 */
internal fun pickVideoFile(
    files: List<TorrentFileEntry>,
    fileName: String?,
    fileIndex: Int?,
    season: Int? = null,
    episode: Int? = null,
): Int? {
    val videos = files.indices.filter { files[it].path.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS }
    if (videos.isEmpty()) return null

    if (episode != null) {
        pickEpisode(videos, season, episode) { baseName(files[it].path) }?.let { return it }
    }

    if (!fileName.isNullOrBlank()) {
        val clean = baseName(fileName)
        videos.firstOrNull { idx ->
            val path = files[idx].path
            baseName(path).equals(clean, ignoreCase = true) || path.endsWith(clean, ignoreCase = true)
        }?.let { return it }
    }

    if (fileIndex != null && fileIndex in videos) return fileIndex

    // Season packs with unrecognised names: the episode-th video, sorted by name.
    if (episode != null && episode in 1..videos.size) {
        return videos.sortedBy { files[it].path }[episode - 1]
    }

    return videos.maxByOrNull { files[it].size }
}

private fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')
