package dev.dogetime.data.addons

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.WatchTarget
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamAddonTest {
    private val requests = mutableListOf<String>()
    private var manifest = TORRENTIO_MANIFEST

    private val client = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            addHandler { request ->
                val url = request.url.toString()
                requests += url
                val json = headersOf("Content-Type", "application/json")
                when {
                    url.endsWith("/manifest.json") -> respond(manifest, HttpStatusCode.OK, json)
                    "/stream/" in url -> respond(STREAMS, HttpStatusCode.OK, json)
                    else -> respond("{}", HttpStatusCode.NotFound, json)
                }
            }
        }
    }
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun pastedLinksBecomeTheBaseUrl() {
        assertEquals("https://torrentio.strem.fun", addonBaseUrl(" https://torrentio.strem.fun/manifest.json "))
        assertEquals("https://torrentio.strem.fun/sort=seeders", addonBaseUrl("stremio://torrentio.strem.fun/sort=seeders/manifest.json"))
        assertEquals("https://a.example/x", addonBaseUrl("https://a.example/x/"))
        assertFailsWith<AddonException> { addonBaseUrl("torrentio.strem.fun") }
    }

    @Test
    fun manifestsWithStringOrObjectResourcesAreRead() {
        val plain = parseStreamManifest(TORRENTIO_MANIFEST, "https://t.example")
        assertEquals("Torrentio", plain.name)
        assertEquals(listOf("tt", "kitsu"), plain.idPrefixes)
        assertTrue(plain.configurable)

        val detailed = parseStreamManifest(
            """{"id":"x","name":"X","types":["movie"],"resources":["catalog",{"name":"stream","types":["series"],"idPrefixes":["tt"]}]}""",
            "https://x.example",
        )
        assertEquals(listOf("series"), detailed.types)
        assertEquals(listOf("tt"), detailed.idPrefixes)
    }

    @Test
    fun addOnsWithoutStreamsAreRefused() {
        val e = assertFailsWith<AddonException> {
            parseStreamManifest("""{"id":"subs","name":"Subs","resources":["subtitles"],"types":["movie"]}""", "https://s.example")
        }
        assertTrue(e.message!!.contains("doesn't provide streams"))
        assertFailsWith<AddonException> { parseStreamManifest("<html>", "https://s.example") }
    }

    @Test
    fun theRightIdIsUsedForEachTitle() {
        val torrentio = parseStreamManifest(TORRENTIO_MANIFEST, "https://t.example")
        assertEquals("series/kitsu:7442:5.json", torrentio.streamPath(WatchTarget.Episode(7442, 1, 5, imdbId = "tt2560140", kitsuId = 7442)))
        assertEquals("series/tt31510819:1:2.json", torrentio.streamPath(WatchTarget.Episode(247718, 1, 2, imdbId = "tt31510819")))
        assertEquals("movie/tt0137523.json", torrentio.streamPath(WatchTarget.Movie(550, imdbId = "tt0137523")))

        val imdbOnly = torrentio.copy(idPrefixes = listOf("tt"))
        // Anime with both ids falls back to IMDb when the add-on doesn't know Kitsu.
        assertEquals("series/tt2560140:1:5.json", imdbOnly.streamPath(WatchTarget.Episode(7442, 1, 5, imdbId = "tt2560140", kitsuId = 7442)))
        assertNull(imdbOnly.streamPath(WatchTarget.Episode(7442, 1, 5, kitsuId = 7442)))
        assertNull(torrentio.copy(types = listOf("movie")).streamPath(WatchTarget.Episode(1, 1, 1, imdbId = "tt1")))
    }

    @Test
    fun torrentsMagnetsAndDirectLinksAreListedOthersSkipped() = runBlocking {
        val addon = parseStreamManifest(TORRENTIO_MANIFEST, "https://t.example/conf")
        val streams = TorrentioApi(client).streams(WatchTarget.Movie(550, imdbId = "tt0137523"), addon)
        assertEquals("https://t.example/conf/stream/movie/tt0137523.json", requests.last())
        assertEquals(3, streams.size) // the YouTube one is skipped

        val torrent = streams[0]
        assertTrue(torrent.isTorrent)
        assertTrue(torrent.magnet.startsWith("magnet:?xt=urn:btih:aaaa"))
        assertEquals("1080p", torrent.quality)
        assertEquals(42, torrent.seeders)
        assertEquals("Torrentio", torrent.addonName)

        assertTrue(streams[1].isTorrent) // a magnet in `url`
        assertTrue(streams[1].magnet.contains("bbbb"))

        val direct = streams[2]
        assertEquals("https://cdn.example/movie.mkv", direct.directUrl)
        assertEquals(mapOf("Referer" to "https://cdn.example"), direct.headers)
        assertEquals("2160p", direct.quality)
        assertEquals("2.0 GB", direct.size)
    }

    @Test
    fun installingReplacingRemovingAndReordering() = runBlocking {
        val repo = StreamAddonRepository(db.preferenceDao(), client)
        assertEquals(StreamAddon.Defaults.map { it.id }, repo.current().map { it.id })

        manifest = """{"id":"org.example.debrid","name":"Debrid","version":"1.0.0","resources":["stream"],"types":["movie","series"],"idPrefixes":["tt"]}"""
        repo.install("https://debrid.example/key=1/manifest.json")
        assertEquals("Debrid", repo.current().last().name)

        // The same add-on again (new configuration): replaced in place, not added twice.
        repo.install("https://debrid.example/key=2/manifest.json")
        assertEquals(1, repo.current().count { it.id == "org.example.debrid" })
        assertEquals("https://debrid.example/key=2", repo.current().last().baseUrl)

        repo.move("org.example.debrid", -1)
        assertEquals("org.example.debrid", repo.current()[repo.current().size - 2].id)

        repo.remove("com.stremio.torrentio.addon")
        assertTrue(repo.current().none { it.id == "com.stremio.torrentio.addon" })
        repo.restoreDefaults()
        assertTrue(repo.current().any { it.id == "com.stremio.torrentio.addon" })
        assertEquals(1, repo.current().count { it.id == "org.example.debrid" })
    }

    private companion object {
        val TORRENTIO_MANIFEST = """
            {"id":"com.stremio.torrentio.addon","version":"0.0.15","name":"Torrentio",
             "resources":["stream"],"types":["movie","series","anime","other"],"catalogs":[],
             "idPrefixes":["tt","kitsu"],"behaviorHints":{"configurable":true,"configurationRequired":false}}
        """.trimIndent()

        val STREAMS = """
            {"streams":[
              {"name":"Torrentio\n1080p","title":"Fight.Club.1999.1080p.BluRay\n👤 42 💾 2.1 GB ⚙️ ThePirateBay","infoHash":"aaaa","fileIdx":0},
              {"name":"Other 720p","title":"Fight Club 720p","url":"magnet:?xt=urn:btih:bbbb&dn=x"},
              {"name":"Debrid\n2160p","title":"Fight Club 4K","url":"https://cdn.example/movie.mkv",
               "behaviorHints":{"filename":"Fight.Club.2160p.mkv","videoSize":2147483648,"proxyHeaders":{"request":{"Referer":"https://cdn.example"}}}},
              {"name":"Trailer","ytId":"abc123"}
            ]}
        """.trimIndent()
    }
}
