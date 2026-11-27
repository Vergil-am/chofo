package dev.dogetime.navigation

import androidx.compose.runtime.saveable.SaverScope
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorTest {
    private val saverScope = SaverScope { true }

    private fun Navigator.saveAndRestore(): Navigator? =
        with(Navigator.Saver) { restore(saverScope.save(this@saveAndRestore)!!) }

    @Test
    fun backStackSurvivesSaveAndRestore() {
        val movie = MediaItem(550, MediaType.Movie, "Fight Club", "", null, null, 8.4, "1999")
        val navigator = Navigator()
        navigator.selectTab(Destination.Browse)
        navigator.open(Destination.Details(MediaType.Movie, 550, movie))
        navigator.open(Destination.Watch(WatchTarget.Episode(1399, 2, 5, imdbId = "tt0944947"), "GoT", null, movie))
        navigator.open(Destination.OfflineWatch(DownloadItem(id = "abc", title = "Fight Club", magnet = "m", infoHash = "abc")))

        val restored = navigator.saveAndRestore()!!

        assertEquals(Destination.Browse, restored.root)
        assertEquals(navigator.current, restored.current)
        restored.back()
        navigator.back()
        assertEquals(navigator.current, restored.current)
    }

    @Test
    fun unreadableStateStartsOverAtHome() {
        assertEquals(null, Navigator.Saver.restore("not json"))
    }

    @Test
    fun replaceSwapsTheTopScreenOnly() {
        val first = Destination.Watch(WatchTarget.Episode(1, 1, 1), "Show S1E1")
        val second = first.copy(target = WatchTarget.Episode(1, 1, 2), title = "Show S1E2")
        val navigator = Navigator(listOf(Destination.Home, first))
        navigator.replace(second)
        assertEquals(second, navigator.current)
        navigator.back()
        assertEquals(Destination.Home, navigator.current)
    }
}
