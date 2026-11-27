package dev.dogetime.core

import dev.dogetime.torrent.TorrentFile
import dev.dogetime.torrent.TorrentStreamServer
import dev.dogetime.torrent.prepareTorrentLibrary
import dev.dogetime.torrent.torrentDataDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val METADATA_TIMEOUT_MS = 90_000L
private const val STATUS_POLL_MS = 1_000L
private const val METADATA_POLL_MS = 500L
private const val REMOVE_TIMEOUT_MS = 10_000L
private const val REMOVE_POLL_MS = 50L

/** One libtorrent session for the whole app, started on the first magnet link. */
actual object Torrents {
    actual val isSupported: Boolean = true

    private val startLock = Mutex()
    private var session: SessionManager? = null

    /** Stopping and cleaning up blocks, so it runs here rather than on the caller's thread. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Removals still running, by info hash; see [remove]. */
    private val removing = ConcurrentHashMap<Sha1Hash, Job>()

    /** Numbers each open's folder, so a late file deletion of a closed copy can't reach a new one. */
    private val openCount = AtomicInteger()

    internal suspend fun session(): SessionManager = startLock.withLock {
        session ?: withContext(Dispatchers.IO) {
            prepareTorrentLibrary()
            // Nothing is kept between runs: torrents are only stored while they're watched.
            torrentDataDir().deleteRecursively()
            // libtorrent's default disk back-end memory-maps files and installs SIGSEGV/SIGBUS
            // handlers that kill the process on any fault they didn't cause. The JVM raises those
            // signals on purpose (null checks, safepoints), so the plain file back-end is used.
            val params = SessionParams().apply { setPosixDiskIO() }
            SessionManager().apply { start(params) }.also { session = it }
        }
    }

    actual suspend fun open(
        magnet: String,
        fileName: String?,
        fileIndex: Int?,
        season: Int?,
        episode: Int?,
        onProgress: (TorrentProgress) -> Unit,
    ): TorrentStream {
        // Started first: parsing the link already needs the native library.
        val session = session()
        val hash = try {
            AddTorrentParams.parseMagnetUri(magnet.trim()).infoHashes.best
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("That isn't a valid magnet link.", e)
        }

        return withContext(Dispatchers.IO) {
            // Replaying a torrent right after leaving it: the old copy must be gone first, or
            // libtorrent hands back that copy and its removal then kills this stream.
            removing[hash]?.join()
            val saveDir = File(torrentDataDir(), "${hash.toHex()}-${openCount.incrementAndGet()}").apply { mkdirs() }
            session.download(magnet.trim(), saveDir, TorrentFlags.SEQUENTIAL_DOWNLOAD)
            var handle: TorrentHandle? = null
            try {
                val info = withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                    var found: TorrentInfo? = null
                    while (found == null) {
                        // The torrent is added asynchronously, so it may not be there yet.
                        val current = handle ?: session.find(hash)
                        handle = current
                        if (current != null) {
                            val status = current.status()
                            onProgress(TorrentProgress(peers = status.numPeers()))
                            if (status.hasMetadata()) found = current.torrentFile()?.takeIf { it.isValid }
                        }
                        if (found == null) delay(METADATA_POLL_MS)
                    }
                    found
                } ?: throw IllegalStateException(
                    "No peers sent this torrent's file list. The magnet link may have no seeders left.",
                )
                val added = handle!!
                val index = videoFileIndex(info, fileName, fileIndex, season, episode)
                    ?: throw IllegalStateException("This torrent has no video file.")

                // Only the video file is downloaded.
                added.prioritizeFiles(
                    Array(info.numFiles()) { if (it == index) Priority.DEFAULT else Priority.IGNORE },
                )
                val file = TorrentFile(added, info, index, saveDir)
                val server = TorrentStreamServer(file)
                val url = server.start()
                OpenTorrent(added, hash, file, server, url)
            } catch (e: Throwable) {
                // Also reached when the user leaves before the video starts.
                (handle ?: session.find(hash))?.let { remove(it, hash) }
                throw e
            }
        }
    }

    /** The file to play in [info]; see [pickVideoFile]. */
    internal fun videoFileIndex(
        info: TorrentInfo,
        fileName: String?,
        fileIndex: Int?,
        season: Int? = null,
        episode: Int? = null,
    ): Int? {
        val files = info.files()
        val entries = (0 until files.numFiles()).map { TorrentFileEntry(files.fileName(it), files.fileSize(it)) }
        return pickVideoFile(entries, fileName, fileIndex, season, episode)
    }

    /**
     * Runs [before], then removes [handle] and deletes its files, in the background. It is
     * registered right away, so an [open] of the same [hash] waits until libtorrent has let go of it.
     */
    private fun remove(handle: TorrentHandle, hash: Sha1Hash, before: () -> Unit = {}) {
        val job = background.launch {
            before()
            val session = session ?: return@launch
            if (handle.isValid) session.remove(handle, SessionHandle.DELETE_FILES)
            // libtorrent removes torrents asynchronously.
            withTimeoutOrNull(REMOVE_TIMEOUT_MS) {
                while (session.find(hash) != null) delay(REMOVE_POLL_MS)
            }
        }
        removing[hash] = job
        job.invokeOnCompletion { removing.remove(hash, job) }
    }

    private class OpenTorrent(
        private val handle: TorrentHandle,
        private val hash: Sha1Hash,
        private val file: TorrentFile,
        private val server: TorrentStreamServer,
        url: String,
    ) : TorrentStream {
        override val name: String = file.name
        override val stream = CapturedStream(url, emptyMap())

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _progress = MutableStateFlow(TorrentProgress(hasMetadata = true, size = file.size))
        override val progress: StateFlow<TorrentProgress> = _progress

        init {
            scope.launch {
                while (isActive && handle.isValid) {
                    val status = handle.status()
                    _progress.value = TorrentProgress(
                        hasMetadata = true,
                        peers = status.numPeers(),
                        downloadRate = status.downloadRate().toLong(),
                        progress = status.progress(),
                        size = file.size,
                        bufferedBytes = file.downloadedFromStart(MAX_COUNTED_BUFFER_BYTES),
                    )
                    delay(STATUS_POLL_MS)
                }
            }
        }

        override fun close() {
            scope.cancel()
            remove(handle, hash) {
                server.stop()
                file.close()
            }
        }
    }
}
