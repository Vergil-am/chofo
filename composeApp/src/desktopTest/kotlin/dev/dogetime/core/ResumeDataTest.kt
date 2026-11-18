package dev.dogetime.core

import dev.dogetime.torrent.prepareTorrentLibrary
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips libtorrent resume data through a real session, with a torrent built from a local
 * file so no network is needed: save it, read the metadata back, and re-add the torrent from it.
 */
class ResumeDataTest {
    private val tempDir: File = Files.createTempDirectory("dogetime-resume-test").toFile()
    private val dataDir = File(tempDir, "data").apply { mkdirs() }
    private val resumeFile = File(dataDir, ".chofo.resume")
    private val videoBytes = Random(42).nextBytes(3 * 1024 * 1024)
    private lateinit var session: SessionManager
    private lateinit var info: TorrentInfo

    @BeforeTest
    fun setUp() {
        prepareTorrentLibrary()
        File(dataDir, "movie.mkv").writeBytes(videoBytes)
        info = TorrentInfo(singleFileTorrent("movie.mkv", videoBytes))
        session = SessionManager().apply { start(SessionParams().apply { setPosixDiskIO() }) }
        session.addListener(resumeDataWriter { hashHex -> resumeFile.takeIf { hashHex == info.infoHash().toHex() } })
    }

    @AfterTest
    fun tearDown() {
        session.stop()
        tempDir.deleteRecursively()
    }

    @Test
    fun savedResumeDataRestoresTorrentWithoutPeers() {
        session.download(info, dataDir)
        val handle = waitFor("torrent to be added") { session.find(info.infoHash())?.takeIf { it.isValid } }
        waitForComplete(handle)

        handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        waitFor("resume file to be written") { resumeFile.takeIf { it.isFile && it.length() > 0 } }

        val restored = assertNotNull(readSavedTorrentInfo(resumeFile))
        assertEquals(info.infoHash(), restored.infoHash())
        assertEquals(info.numFiles(), restored.numFiles())

        // A fresh add from the saved data has its metadata at once and keeps the finished pieces.
        session.remove(handle)
        waitFor("torrent to be removed") { session.find(info.infoHash()).let { if (it == null || !it.isValid) true else null } }
        session.download(restored, dataDir, resumeFile, Priority.array(Priority.DEFAULT, restored.numFiles()), null, TorrentFlags.AUTO_MANAGED)
        val readded = waitFor("torrent to be re-added") { session.find(info.infoHash())?.takeIf { it.isValid } }
        assertTrue(readded.status().hasMetadata())
        waitForComplete(readded)
    }

    @Test
    fun unreadableResumeFileGivesNull() {
        assertNull(readSavedTorrentInfo(File(dataDir, "missing.resume")))
        resumeFile.writeText("not bencoded data")
        assertNull(readSavedTorrentInfo(resumeFile))
    }

    /**
     * A v1 .torrent for one file, bencoded by hand. libtorrent's TorrentBuilder isn't used: it hashes
     * through the memory-mapped disk back-end, whose SIGSEGV handler kills the JVM (see Torrents.jvm.kt).
     */
    private fun singleFileTorrent(name: String, data: ByteArray, pieceLength: Int = 256 * 1024): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val pieces = ByteArrayOutputStream()
        for (offset in data.indices step pieceLength) {
            pieces.write(sha1.digest(data.copyOfRange(offset, minOf(offset + pieceLength, data.size))))
        }
        return ByteArrayOutputStream().apply {
            fun str(s: String) = write("${s.length}:$s".toByteArray())
            write("d4:infod".toByteArray())
            str("length"); write("i${data.size}e".toByteArray())
            str("name"); str(name)
            str("piece length"); write("i${pieceLength}e".toByteArray())
            str("pieces"); write("${pieces.size()}:".toByteArray()); write(pieces.toByteArray())
            write("ee".toByteArray())
        }.toByteArray()
    }

    private fun waitForComplete(handle: TorrentHandle) = waitFor("all pieces to be verified") {
        handle.fileProgress(TorrentHandle.PIECE_GRANULARITY).firstOrNull()?.takeIf { it == videoBytes.size.toLong() }
    }

    private fun <T : Any> waitFor(what: String, timeoutMs: Long = 15_000, check: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            check()?.let { return it }
            Thread.sleep(50)
        }
        error("Timed out waiting for $what")
    }
}
