package dev.dogetime.core

/**
 * Picks the item whose file name is for [episode] of [season] (season 1 when unknown): first one
 * tagged with both, like `S01E05` or `1x05`, then one tagged only with the episode, like `E05` or
 * `Ep 05`. Returns null when nothing matches.
 *
 * Number boundaries are explicit rather than `\b`, so episode 1 never matches `E10` or `E12`, and
 * `_` counts as a separator (`\b` treats it as a letter, so `Show_S01E05_eng` wouldn't match).
 */
internal fun <T> pickEpisode(items: List<T>, season: Int?, episode: Int, fileName: (T) -> String): T? {
    val s = season ?: 1
    val seasonAndEpisode = listOf(
        Regex("""(?i)(?<![a-z0-9])s0*$s[.\-_ ]*e0*$episode(?![0-9])"""),
        Regex("""(?i)(?<![a-z0-9])0*${s}x0*$episode(?![0-9])"""),
    )
    val episodeOnly = Regex("""(?i)(?<![a-z0-9])(?:episode|ep|e)[.\-_ ]*0*$episode(?![0-9])""")
    return items.firstOrNull { item -> seasonAndEpisode.any { it.containsMatchIn(fileName(item)) } }
        ?: items.firstOrNull { item -> episodeOnly.containsMatchIn(fileName(item)) }
}
