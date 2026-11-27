package dev.dogetime.data

import dev.dogetime.domain.SubtitleProvider
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtsSubsParserTest {
    private val base = "https://yts-subs.com"

    private fun page(rows: String) = """
        <html><body><table>
          <thead><tr><th>Rating</th><th>Language</th><th>Release</th><th>Uploader</th></tr></thead>
          <tbody>$rows</tbody>
        </table></body></html>
    """.trimIndent()

    @Test
    fun parsesRows() {
        val html = page(
            """
            <tr>
              <td>5</td>
              <td><span class="sub-lang">English</span></td>
              <td><a href="/subtitles/movie-2019-english-yify-1">subtitle Movie.2019.1080p.BluRay</a></td>
              <td class="uploader-cell">someone</td>
            </tr>
            <tr>
              <td>2</td>
              <td><span class="sub-lang"> French </span></td>
              <td><a href="https://yts-subs.com/subtitles/movie-2019-french-yify-2">subtitle Movie.2019.720p</a></td>
              <td class="uploader-cell"></td>
            </tr>
            """,
        )
        val subs = parseYtsSubsMoviePage(html, base)

        assertEquals(2, subs.size)
        with(subs[0]) {
            assertEquals("English", language)
            assertEquals("Movie.2019.1080p.BluRay", releaseName)
            assertEquals("someone", author)
            assertEquals("$base/subtitles/movie-2019-english-yify-1", downloadUrl)
            assertEquals(SubtitleProvider.YtsSubs, provider)
        }
        // Absolute links are kept as they are.
        assertEquals("https://yts-subs.com/subtitles/movie-2019-french-yify-2", subs[1].downloadUrl)
        assertEquals("French", subs[1].language)
        assertEquals("", subs[1].author)
    }

    @Test
    fun blankReleaseNameGetsADefault() {
        val html = page(
            """<tr><td></td><td><span class="sub-lang">English</span></td>
               <td><a href="/subtitles/x">subtitle</a></td></tr>""",
        )
        assertEquals("YTS Subtitle", parseYtsSubsMoviePage(html, base).single().releaseName)
    }

    @Test
    fun skipsRowsWithoutLanguageOrLink() {
        val html = page(
            """
            <tr><td></td><td><span class="sub-lang"></span></td><td><a href="/subtitles/a">subtitle A</a></td></tr>
            <tr><td></td><td><span class="sub-lang">English</span></td><td><a href="/other/b">subtitle B</a></td></tr>
            <tr><td></td><td><span class="sub-lang">English</span></td><td>no link</td></tr>
            """,
        )
        assertTrue(parseYtsSubsMoviePage(html, base).isEmpty())
    }

    @Test
    fun pageWithoutTableGivesNothing() {
        assertTrue(parseYtsSubsMoviePage("<html><body>Not found</body></html>", base).isEmpty())
    }

    @Test
    fun decodesTheDownloadLink() {
        val link = "https://yts-subs.com/subtitle/movie-english.zip"
        val encoded = Base64.getEncoder().encodeToString(link.toByteArray())
        val html = """<a id="btn-download-subtitle" data-link="$encoded">Download</a>"""
        assertEquals(link, parseYtsSubsDownloadUrl(html))
    }

    @Test
    fun missingOrBrokenDownloadLinkGivesNull() {
        assertNull(parseYtsSubsDownloadUrl("<html><body></body></html>"))
        assertNull(parseYtsSubsDownloadUrl("""<a id="btn-download-subtitle" data-link="">Download</a>"""))
        assertNull(parseYtsSubsDownloadUrl("""<a id="btn-download-subtitle" data-link="%%%not base64">Download</a>"""))
    }
}
