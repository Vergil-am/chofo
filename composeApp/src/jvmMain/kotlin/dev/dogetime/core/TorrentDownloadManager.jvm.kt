package dev.dogetime.core

import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.Subtitle
import dev.dogetime.torrent.DownloadNotifier
import dev.dogetime.torrent.torrentDownloadsDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

private const val METADATA_TIMEOUT_MS = 60_000L
private const val POLL_INTERVAL_MS = 1_000L

/** How often, in progress polls, libtorrent's resume data is saved while downloading. */
private const val RESUME_SAVE_EVERY_POLLS = 15

/**
 * libtorrent's resume data (the torrent's metadata plus which pieces are done), kept in each
 * torrent's download folder so a restarted download needs no peers for metadata and no full
 * re-check of the file. Deleted with the folder when the download is cancelled.
 */
private const val RESUME_FILE_NAME = ".chofo.resume"

/** Subtitle files inside a torrent are always downloaded alongside the wanted videos. */
private val SUBTITLE_EXTENSIONS = listOf(".srt", ".vtt", ".ass")

actual class TorrentDownloadManager actual constructor(
    actual val repository: DownloadRepository,
    private val subtitleDownloader: SubtitleDownloader,
) {
    actual val isSupported: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()

    // Track active wanted file indices per torrent infoHash to support multiple episodes from the same torrent
    private val wantedFilesByHash = ConcurrentHashMap<String, MutableSet<Int>>()

    /** Resume-data file per torrent info hash, for the alert listener to write to. */
    private val resumeFiles = ConcurrentHashMap<String, File>()
    private var listenerSession: SessionManager? = null

    init {
        // Downloads that were running when the app was closed or killed carry on where they left off.
        scope.launch {
            runCatching { repository.getByStatus(DownloadStatus.DOWNLOADING) }
                .getOrDefault(emptyList())
                .forEach { startDownload(it, null) }
        }
    }

    /** Writes resume data to disk whenever libtorrent hands it over after [TorrentHandle.saveResumeData]. */
    @Synchronized
    private fun ensureResumeListener(session: SessionManager) {
        if (listenerSession === session) return
        session.addListener(resumeDataWriter { hashHex -> resumeFiles[hashHex] })
        listenerSession = session
    }

    /** Asks libtorrent for fresh resume data; it arrives asynchronously in the listener above. */
    private fun requestResumeSave(handle: TorrentHandle?) {
        if (handle == null || !handle.isValid) return
        runCatching { handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
    }

    actual fun startDownload(
        item: DownloadItem,
        selectedSubtitle: Subtitle?,
    ) {
        // Cancel existing job if active for this download item
        activeJobs[item.id]?.cancel()

        val job = scope.launch {
            // The subtitle is saved early (see below), so a resumed download already has it. Every
            // write goes through latest() so progress updates never wipe the saved subtitle.
            var subtitlePath = item.subtitleFilePath?.takeIf { File(it).isFile }
            var subtitleLanguage = item.subtitleLanguage.takeIf { subtitlePath != null }
            fun latest() = item.copy(subtitleFilePath = subtitlePath, subtitleLanguage = subtitleLanguage)

            /** Fetches [selectedSubtitle] next to the video. Returns false if it couldn't. */
            suspend fun fetchSelectedSubtitle(videoPath: String): Boolean {
                val subtitle = selectedSubtitle ?: return false
                val saved = runCatching { subtitleDownloader.downloadAndSaveSidecar(videoPath, subtitle) }
                    .getOrNull() ?: return false
                subtitlePath = saved
                subtitleLanguage = subtitle.language
                return true
            }

            /** Marks this download failed, in the database and the notification. */
            suspend fun fail(message: String) {
                DownloadNotifier.notifyFailed(id = item.id, title = item.title, error = message)
                repository.upsert(latest().copy(status = DownloadStatus.ERROR, errorMessage = message))
            }

            try {
                repository.upsert(
                    latest().copy(
                        status = DownloadStatus.DOWNLOADING,
                        errorMessage = null,
                    ),
                )
                DownloadNotifier.notifyProgress(
                    id = item.id,
                    title = item.title,
                    progress = 0f,
                    downloadRate = 0L,
                    peers = 0,
                )

                val session = Torrents.session()
                val hash = try {
                    AddTorrentParams.parseMagnetUri(item.magnet.trim()).infoHashes.best
                } catch (e: Exception) {
                    fail("Invalid magnet link: ${e.message}")
                    return@launch
                }

                val hashHex = hash.toHex()
                val saveDir = File(torrentDownloadsDir(), hashHex).apply { mkdirs() }

                val resumeFile = File(saveDir, RESUME_FILE_NAME)
                resumeFiles[hashHex] = resumeFile
                ensureResumeListener(session)

                // Another episode of the same torrent may already have it in the session.
                if (session.find(hash)?.isValid != true) {
                    addTorrent(session, item.magnet.trim(), saveDir, resumeFile)
                }

                var handle: TorrentHandle? = null
                val info = withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                    var found: TorrentInfo? = null
                    while (found == null && isActive) {
                        val current = handle ?: session.find(hash)
                        handle = current
                        if (current != null) {
                            activeHandles[item.id] = current
                            val status = current.status()
                            repository.upsert(
                                latest().copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    peers = status.numPeers(),
                                ),
                            )
                            DownloadNotifier.notifyProgress(
                                id = item.id,
                                title = item.title,
                                progress = 0f,
                                downloadRate = 0L,
                                peers = status.numPeers(),
                            )
                            if (status.hasMetadata()) {
                                found = current.torrentFile()?.takeIf { it.isValid }
                            }
                        }
                        if (found == null) delay(500)
                    }
                    found
                }

                val liveHandle = handle
                if (info == null || liveHandle == null) {
                    fail("Metadata timeout. No peers found for this torrent.")
                    return@launch
                }

                val targetHandle = liveHandle
                activeHandles[item.id] = targetHandle
                // pauseDownload() may have paused the handle; this download wants it running.
                targetHandle.resume()

                val videoIndex = Torrents.videoFileIndex(info, item.fileName, item.fileIndex, item.season, item.episode)
                if (videoIndex == null) {
                    fail("No video file found in this torrent for ${item.title}.")
                    return@launch
                }

                val files = info.files()
                val videoFilePath = files.filePath(videoIndex, saveDir.absolutePath)
                val targetVideoFile = File(videoFilePath)
                val targetFileSize = files.fileSize(videoIndex).coerceAtLeast(1L)

                // Register this file as wanted for this torrent hash
                val wantedSet = wantedFilesByHash.getOrPut(hashHex) { ConcurrentHashMap.newKeySet() }
                wantedSet.add(videoIndex)

                applyPriorities(targetHandle, hashHex)

                // Fetch the picked subtitle now rather than at the end: it's a few KB, the provider's
                // link may expire before a long download finishes, and once it's on disk and in the
                // database it survives the app being closed (only the video is resumed then).
                if (subtitlePath == null && fetchSelectedSubtitle(targetVideoFile.absolutePath)) {
                    repository.upsert(
                        latest().copy(
                            status = DownloadStatus.DOWNLOADING,
                            fileIndex = videoIndex,
                            videoFilePath = targetVideoFile.absolutePath,
                        ),
                    )
                }

                // Track download progress for this specific file
                var polls = 0
                while (isActive && targetHandle.isValid) {
                    if (++polls % RESUME_SAVE_EVERY_POLLS == 0 && targetHandle.needSaveResumeData()) {
                        requestResumeSave(targetHandle)
                    }
                    val st = targetHandle.status()
                    val fileProgressArray = runCatching { targetHandle.fileProgress() }.getOrNull()
                    val fileDone = (fileProgressArray?.getOrNull(videoIndex) ?: 0L).coerceIn(0L, targetFileSize)
                    val fileProgressRatio = (fileDone.toFloat() / targetFileSize.toFloat()).coerceIn(0f, 1f)
                    // The default count above includes blocks that haven't passed their hash check, so
                    // it only drives the progress bar. Completion needs every piece of the file verified.
                    // (File length is no proof either: libtorrent writes sparse files out of order.)
                    val verifiedDone = runCatching {
                        targetHandle.fileProgress(TorrentHandle.PIECE_GRANULARITY).getOrNull(videoIndex)
                    }.getOrNull() ?: 0L
                    val isFinished = verifiedDone >= targetFileSize

                    if (isFinished) {
                        // The early fetch may have failed (e.g. offline at the time): try once more.
                        if (subtitlePath == null) fetchSelectedSubtitle(targetVideoFile.absolutePath)
                        if (subtitlePath == null) {
                            // Use a subtitle that came in the torrent: one named after the video, or
                            // failing that, for episodes, one tagged with this episode.
                            val videoBase = targetVideoFile.nameWithoutExtension
                            val torrentSubs = (0 until info.numFiles())
                                .map { File(files.filePath(it, saveDir.absolutePath)) }
                                .filter { f ->
                                    f.exists() && (f.name.endsWith(".srt", ignoreCase = true) || f.name.endsWith(".vtt", ignoreCase = true))
                                }
                            val embeddedSub = torrentSubs.firstOrNull { it.nameWithoutExtension.startsWith(videoBase, ignoreCase = true) }
                                ?: item.episode?.let { ep -> pickEpisode(torrentSubs, item.season, ep) { it.name } }
                            subtitlePath = embeddedSub?.absolutePath
                        }

                        // Remove this file from wanted set since it is completed
                        wantedSet.remove(videoIndex)
                        applyPriorities(targetHandle, hashHex)

                        repository.upsert(
                            latest().copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 1f,
                                downloadedBytes = targetFileSize,
                                totalBytes = targetFileSize,
                                fileIndex = videoIndex,
                                downloadRate = 0L,
                                peers = st.numPeers(),
                                videoFilePath = targetVideoFile.absolutePath,
                            ),
                        )
                        DownloadNotifier.notifyCompleted(item.id, item.title)
                        break
                    } else {
                        repository.upsert(
                            latest().copy(
                                status = DownloadStatus.DOWNLOADING,
                                progress = fileProgressRatio,
                                downloadedBytes = fileDone,
                                totalBytes = targetFileSize,
                                fileIndex = videoIndex,
                                downloadRate = st.downloadRate().toLong(),
                                peers = st.numPeers(),
                                videoFilePath = targetVideoFile.absolutePath,
                            ),
                        )
                        DownloadNotifier.notifyProgress(
                            id = item.id,
                            title = item.title,
                            progress = fileProgressRatio,
                            downloadRate = st.downloadRate().toLong(),
                            peers = st.numPeers(),
                        )
                    }

                    delay(POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                // Stopped or paused
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Download failed")
            } finally {
                activeJobs.remove(item.id)
            }
        }

        activeJobs[item.id] = job
    }

    actual fun pauseDownload(id: String) {
        DownloadNotifier.cancel(id)
        activeJobs.remove(id)?.cancel()
        scope.launch {
            val item = repository.getById(id)
            if (item != null) {
                val hashHex = item.infoHash
                val wantedSet = wantedFilesByHash[hashHex]
                if (item.fileIndex != null) {
                    wantedSet?.remove(item.fileIndex)
                }
                val handle = activeHandles[id]
                requestResumeSave(handle)
                if (wantedSet.isNullOrEmpty()) {
                    handle?.pause()
                } else if (handle != null) {
                    applyPriorities(handle, hashHex)
                }
            }
            repository.updateStatus(id, DownloadStatus.PAUSED)
        }
    }

    actual fun resumeDownload(id: String) {
        scope.launch {
            val item = repository.getById(id) ?: return@launch
            startDownload(item, null)
        }
    }

    actual fun cancelDownload(id: String, deleteFiles: Boolean) {
        DownloadNotifier.cancel(id)
        activeJobs.remove(id)?.cancel()
        val handle = activeHandles.remove(id)

        scope.launch {
            val item = repository.getById(id)
            if (item != null) {
                val hashHex = item.infoHash
                val wantedSet = wantedFilesByHash[hashHex]
                if (item.fileIndex != null) {
                    wantedSet?.remove(item.fileIndex)
                }

                if (deleteFiles) {
                    item.videoFilePath?.let { File(it).delete() }
                    item.subtitleFilePath?.let { File(it).delete() }
                }

                val remainingItems = repository.getByInfoHash(hashHex).filter { it.id != id }
                val hasOtherActive = remainingItems.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED
                }

                if (!hasOtherActive) {
                    wantedFilesByHash.remove(hashHex)
                    if (handle != null && handle.isValid) {
                        val session = runCatching { Torrents.session() }.getOrNull()
                        if (deleteFiles && remainingItems.isEmpty()) {
                            session?.remove(handle, SessionHandle.DELETE_FILES)
                            val saveDir = File(torrentDownloadsDir(), hashHex)
                            saveDir.deleteRecursively()
                        } else {
                            session?.remove(handle)
                        }
                    }
                } else if (handle != null) {
                    applyPriorities(handle, hashHex)
                }
            }
            repository.deleteById(id)
        }
    }

    actual suspend fun resolveOfflinePlayback(item: DownloadItem): DownloadItem = withContext(Dispatchers.IO) {
        val originalVideo = item.videoFilePath
        var resolvedVideo = originalVideo
        if (resolvedVideo == null || !File(resolvedVideo).exists()) {
            val hashDir = File(torrentDownloadsDir(), item.infoHash)
            if (hashDir.exists()) {
                val targetName = if (originalVideo != null) File(originalVideo).name else item.fileName
                val foundByName = if (targetName != null) {
                    hashDir.walkTopDown().firstOrNull { it.isFile && it.name == targetName }
                } else null

                if (foundByName != null && foundByName.exists()) {
                    resolvedVideo = foundByName.absolutePath
                } else {
                    val videos = hashDir.walkTopDown()
                        .filter { it.isFile && it.extension.lowercase() in listOf("mkv", "mp4", "avi", "mov", "webm") }
                        .toList()
                    if (item.episode != null) {
                        val match = pickEpisode(videos, item.season, item.episode) { it.name }
                        if (match != null) resolvedVideo = match.absolutePath
                    } else if (videos.size == 1) {
                        resolvedVideo = videos.first().absolutePath
                    }
                }
            }
        }

        var resolvedSubtitle = item.subtitleFilePath
        if (resolvedSubtitle == null || !File(resolvedSubtitle).exists()) {
            if (resolvedVideo != null) {
                val videoFile = File(resolvedVideo)
                val parent = videoFile.parentFile
                if (parent != null && parent.exists()) {
                    val sidecars = parent.listFiles { f ->
                        f.isFile && (f.name.endsWith(".srt", ignoreCase = true) || f.name.endsWith(".vtt", ignoreCase = true))
                    }
                    val match = sidecars?.firstOrNull {
                        it.nameWithoutExtension.startsWith(videoFile.nameWithoutExtension, ignoreCase = true)
                    } ?: sidecars?.firstOrNull()
                    if (match != null) resolvedSubtitle = match.absolutePath
                }
            }
        }

        if (resolvedVideo != null && !File(resolvedVideo).exists()) {
            resolvedVideo = null
        }

        val updated = item.copy(
            videoFilePath = resolvedVideo,
            subtitleFilePath = resolvedSubtitle,
        )

        if (updated.videoFilePath != item.videoFilePath || updated.subtitleFilePath != item.subtitleFilePath) {
            // Fix the stored paths in the background; playback doesn't need to wait for it.
            scope.launch {
                repository.upsert(updated)
            }
        }

        updated
    }

    /**
     * Sets [handle]'s file priorities from what every download of this torrent still wants: those
     * videos plus any subtitle files, everything else skipped. Several episodes can share a torrent.
     */
    private fun applyPriorities(handle: TorrentHandle, hashHex: String) {
        if (!handle.isValid) return
        val info = handle.torrentFile()?.takeIf { it.isValid } ?: return
        val wanted = wantedFilesByHash[hashHex].orEmpty()
        val files = info.files()
        val priorities = Array(info.numFiles()) { idx ->
            val name = files.fileName(idx).lowercase()
            when {
                idx in wanted -> Priority.DEFAULT
                SUBTITLE_EXTENSIONS.any { name.endsWith(it) } -> Priority.DEFAULT
                else -> Priority.IGNORE
            }
        }
        handle.prioritizeFiles(priorities)
    }

    /**
     * Adds the torrent from its saved resume data when there is some, so metadata comes from disk
     * and finished pieces aren't re-checked. Falls back to the magnet link, which needs peers.
     */
    private fun addTorrent(session: SessionManager, magnet: String, saveDir: File, resumeFile: File) {
        val savedInfo = readSavedTorrentInfo(resumeFile)
        if (savedInfo != null) {
            val added = runCatching {
                // Everything starts ignored; the wanted file is switched on once the handle is found.
                session.download(
                    savedInfo,
                    saveDir,
                    resumeFile,
                    Priority.array(Priority.IGNORE, savedInfo.numFiles()),
                    null,
                    TorrentFlags.AUTO_MANAGED,
                )
            }.isSuccess
            if (added) return
            resumeFile.delete()
        }
        session.download(magnet, saveDir, TorrentFlags.AUTO_MANAGED)
    }
}

/**
 * Saves each torrent's resume data, as libtorrent delivers it, to the file [fileFor] returns for
 * its info hash (hex). Torrents with no file, like ones only being streamed, are skipped.
 */
internal fun resumeDataWriter(fileFor: (String) -> File?): AlertListener = object : AlertListener {
    override fun types(): IntArray = intArrayOf(AlertType.SAVE_RESUME_DATA.swig())

    override fun alert(alert: Alert<*>) {
        val resumeAlert = alert as? SaveResumeDataAlert ?: return
        runCatching {
            val file = fileFor(resumeAlert.handle().infoHash().toHex()) ?: return
            writeAtomically(file, AddTorrentParams.writeResumeDataBuf(resumeAlert.params()))
        }
    }
}

/** The torrent metadata inside a resume-data file, or null if there is none or it's unreadable. */
internal fun readSavedTorrentInfo(file: File): TorrentInfo? = runCatching {
    if (!file.isFile) return null
    val error = error_code()
    val params = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(file.readBytes()), error)
    if (error.value() != 0) return null
    AddTorrentParams(params).torrentInfo?.takeIf { it.isValid }
}.getOrNull()

/** Written next to the target and moved into place, so a kill mid-write never leaves half a file. */
internal fun writeAtomically(file: File, bytes: ByteArray) {
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeBytes(bytes)
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
