package dev.dogetime.data

import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.domain.extractInfoHash

import dev.dogetime.domain.TorrentOption
import dev.dogetime.domain.WatchTarget
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable

// Well-known public trackers find peers faster than DHT alone.
private val TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
)

/**
 * Lists streams for a movie or episode from a Stremio stream add-on: torrents (`infoHash`, or a
 * `magnet:` url) and direct links (`url`, with any `proxyHeaders`). Others (YouTube, external
 * links) are skipped.
 */
class TorrentioApi(private val client: HttpClient) {

    /** Streams [addon] lists for [target], in the add-on's order. Empty when it can't serve it. */
    suspend fun streams(target: WatchTarget, addon: StreamAddon): List<TorrentOption> {
        val path = addon.streamPath(target) ?: return emptyList()
        val response: TorrentioResponse = client.get("${addon.baseUrl}/stream/$path").body()
        return response.streams.mapNotNull { it.toDomain(addon.name) }
    }
}

@Serializable
private data class TorrentioResponse(val streams: List<TorrentioStream> = emptyList())

@Serializable
private data class TorrentioStream(
    val name: String = "",
    val title: String? = null,
    val description: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val url: String? = null,
    val sources: List<String> = emptyList(),
    val behaviorHints: TorrentioHints? = null,
)

@Serializable
private data class TorrentioHints(
    val filename: String? = null,
    val seeders: Int? = null,
    val videoSize: Long? = null,
    val proxyHeaders: ProxyHeaders? = null,
)

@Serializable
private data class ProxyHeaders(val request: Map<String, String> = emptyMap())

private val SEEDERS = Regex("""[👤👥]\s*(\d+)""")
private val SIZE = Regex("""💾\s*([\d.,]+\s*[KMGT]?i?B)""")
private val SITE = Regex("""[⚙🔎🏷]\uFE0F?\s*(.+)""")
private val RESOLUTION_REGEX = Regex("""(?i)\b(2160p|4k|1080p|720p|576p|480p|360p|240p)\b""")

private fun TorrentioStream.toDomain(addonName: String): TorrentOption? {
    val link = url?.trim()?.takeIf { it.isNotEmpty() }
    // A magnet in `url` is a torrent too.
    val hash = infoHash?.takeIf { it.isNotBlank() }
        ?: link?.takeIf { it.startsWith("magnet:", ignoreCase = true) }?.let(::extractInfoHash)
    if (hash == null) {
        val direct = link?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            ?: return null
        return directOption(direct, addonName)
    }
    val rawText = (title ?: description).orEmpty()
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val stats = lines.lastOrNull { "👤" in it || "👥" in it || "💾" in it }.orEmpty()
    val fileName = behaviorHints?.filename
    val release = fileName ?: lines.firstOrNull { it != stats }?.removePrefix("📄")?.trim() ?: hash

    val streamTrackers = sources
        .filter { it.startsWith("tracker:") }
        .map { it.removePrefix("tracker:") }
    val allTrackers = (streamTrackers + TRACKERS).distinct()

    val magnet = buildString {
        append("magnet:?xt=urn:btih:").append(hash)
        append("&dn=").append((fileName ?: release).encodeURLParameter())
        allTrackers.forEach { append("&tr=").append(it.encodeURLParameter()) }
    }

    val quality = run {
        if ('\n' in name) {
            val after = name.substringAfter('\n').trim()
            if (after.isNotEmpty()) return@run after
        }
        RESOLUTION_REGEX.find(name)?.value?.let { return@run it }
        RESOLUTION_REGEX.find(rawText)?.value?.let { return@run it }
        "Unknown"
    }

    return TorrentOption(
        quality = quality,
        release = release,
        seeders = SEEDERS.find(rawText)?.groupValues?.get(1)?.toIntOrNull() ?: behaviorHints?.seeders,
        size = SIZE.find(rawText)?.groupValues?.get(1) ?: behaviorHints?.videoSize?.let(::formatBytes),
        site = SITE.find(rawText)?.groupValues?.get(1)?.trim(),
        magnet = magnet,
        fileIndex = fileIdx,
        fileName = fileName,
        addonName = addonName,
    )
}

/** A direct HTTP(S) stream, often from a debrid service: plays as is, can't be downloaded here. */
private fun TorrentioStream.directOption(link: String, addonName: String): TorrentOption {
    val rawText = (title ?: description).orEmpty()
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val fileName = behaviorHints?.filename
    val quality = name.substringAfter('\n', "").trim().takeIf { it.isNotEmpty() }
        ?: RESOLUTION_REGEX.find(name)?.value
        ?: RESOLUTION_REGEX.find(rawText)?.value
        ?: "Unknown"
    return TorrentOption(
        quality = quality,
        release = fileName ?: lines.firstOrNull() ?: name.ifBlank { addonName },
        seeders = null,
        size = SIZE.find(rawText)?.groupValues?.get(1) ?: behaviorHints?.videoSize?.let(::formatBytes),
        site = null,
        magnet = "",
        fileIndex = null,
        fileName = fileName,
        addonName = addonName,
        directUrl = link,
        headers = behaviorHints?.proxyHeaders?.request.orEmpty(),
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    val rounded = (value * 100).toLong() / 100.0
    return "$rounded ${units[unit]}"
}
