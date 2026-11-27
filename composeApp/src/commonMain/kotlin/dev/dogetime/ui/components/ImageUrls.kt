package dev.dogetime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

private val TMDB_SIZE = Regex("""(image\.tmdb\.org/t/p/)w\d+/""")

/**
 * [url] at a size that stays sharp across [widthPx]. TMDB backdrops are fetched at w780 for the
 * cards, which looks soft stretched over a whole window, so a TMDB image is asked for at w1280,
 * or at its original size on wider screens. Other URLs (e.g. Kitsu's) are returned as they are.
 */
fun sharpImageUrl(url: String, widthPx: Int): String {
    val size = when {
        widthPx <= 780 -> return url
        widthPx <= 1280 -> "w1280"
        else -> "original"
    }
    return url.replace(TMDB_SIZE, "$1$size/")
}

/** [url] sharp enough to fill the window's width, for full-width backdrops. */
@Composable
fun windowWideImageUrl(url: String): String = sharpImageUrl(url, LocalWindowInfo.current.containerSize.width)
