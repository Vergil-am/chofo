package dev.dogetime.domain

/** One stream with the video for a [WatchTarget], as listed by a stream add-on: a torrent or a direct link. */
data class TorrentOption(
    /** Resolution label such as "1080p" or "4k HDR". */
    val quality: String,
    /** The release's name, e.g. "Movie.2024.1080p.WEB-DL.x265". */
    val release: String,
    val seeders: Int?,
    /** Human-readable size of the video file, e.g. "2.01 GB". */
    val size: String?,
    /** The site the torrent was found on. */
    val site: String?,
    val magnet: String,
    /** Which file in the torrent is the video, for torrents of a whole season or series. */
    val fileIndex: Int?,
    val fileName: String?,
    /** The stream add-on that listed it. */
    val addonName: String? = null,
    /** Set for a direct HTTP(S) stream; [magnet] is then empty. */
    val directUrl: String? = null,
    /** Headers [directUrl] needs (Stremio's `proxyHeaders.request`). */
    val headers: Map<String, String> = emptyMap(),
) {
    /** A torrent, as opposed to a direct link: only these can be downloaded. */
    val isTorrent: Boolean get() = directUrl == null
}

/** Extracts the infoHash hex string from a magnet URI. */
fun extractInfoHash(magnet: String): String {
    val prefix = "urn:btih:"
    val index = magnet.indexOf(prefix, ignoreCase = true)
    if (index == -1) return magnet.hashCode().toString()
    val after = magnet.substring(index + prefix.length)
    val end = after.indexOf('&')
    return if (end != -1) after.substring(0, end) else after
}
