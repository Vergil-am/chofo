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
            try {
                repository.upsert(
                    item.copy(
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
                    DownloadNotifier.notifyFailed(
                        id = item.id,
                        title = item.title,
                        error = "Invalid magnet link: ${e.message}",
                    )
                    repository.upsert(
                        item.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = "Invalid magnet link: ${e.message}",
                        ),
                    )
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
                                item.copy(
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
                    DownloadNotifier.notifyFailed(
                        id = item.id,
                        title = item.title,
                        error = "Metadata timeout. No peers found for this torrent.",
                    )
                    repository.upsert(
                        item.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = "Metadata timeout. No peers found for this torrent.",
                        ),
                    )
                    return@launch
                }

                val targetHandle = liveHandle
                activeHandles[item.id] = targetHandle
                // pauseDownload() may have paused the handle; this download wants it running.
                targetHandle.resume()

                val videoIndex = Torrents.videoFileIndex(info, item.fileName, item.fileIndex, item.season, item.episode)
                if (videoIndex == null) {
                    DownloadNotifier.notifyFailed(
                        id = item.id,
                        title = item.title,
                        error = "No video file found in this torrent for ${item.title}.",
                    )
                    repository.upsert(
                        item.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = "No video file found in this torrent for ${item.title}.",
                        ),
                    )
                    return@launch
                }

                val files = info.files()
                val videoFilePath = files.filePath(videoIndex, saveDir.absolutePath)
                val targetVideoFile = File(videoFilePath)
                val targetFileSize = files.fileSize(videoIndex).coerceAtLeast(1L)

                // Register this file as wanted for this torrent hash
                val wantedSet = wantedFilesByHash.getOrPut(hashHex) { ConcurrentHashMap.newKeySet() }
                wantedSet.add(videoIndex)

                // Update torrent file priorities across all active downloads for this hash
                fun updatePriorities() {
                    val activeWanted = wantedFilesByHash[hashHex].orEmpty()
                    val priorities = Array(info.numFiles()) { idx ->
                        val name = files.fileName(idx).lowercase()
                        when {
                            idx in activeWanted -> Priority.DEFAULT
                            name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") -> Priority.DEFAULT
                            else -> Priority.IGNORE
                        }
                    }
                    targetHandle.prioritizeFiles(priorities)
                }
                updatePriorities()

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
                        // Download selected external subtitle sidecar if specified
                        var sidecarPath: String? = null
                        if (selectedSubtitle != null) {
                            sidecarPath = runCatching {
                                subtitleDownloader.downloadAndSaveSidecar(
                                    targetVideoFile.absolutePath,
                                    selectedSubtitle,
                                )
                            }.getOrNull()
                        } else {
                            // Check if an in-torrent subtitle exists specifically for this video/episode
                            val videoBase = targetVideoFile.nameWithoutExtension
                            val embeddedSub = (0 until info.numFiles())
                                .map { File(files.filePath(it, saveDir.absolutePath)) }
                                .firstOrNull { f ->
                                    f.exists() && (f.name.endsWith(".srt", ignoreCase = true) || f.name.endsWith(".vtt", ignoreCase = true)) &&
                                        (f.nameWithoutExtension.startsWith(videoBase, ignoreCase = true) ||
                                         (item.episode != null && f.name.contains("e0*${item.episode}".toRegex(RegexOption.IGNORE_CASE))))
                                }
                            sidecarPath = embeddedSub?.absolutePath
                        }

                        // Remove this file from wanted set since it is completed
                        wantedSet.remove(videoIndex)
                        updatePriorities()

                        repository.upsert(
                            item.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 1f,
                                downloadedBytes = targetFileSize,
                                totalBytes = targetFileSize,
                                fileIndex = videoIndex,
                                downloadRate = 0L,
                                peers = st.numPeers(),
                                videoFilePath = targetVideoFile.absolutePath,
                                subtitleFilePath = sidecarPath,
                                subtitleLanguage = selectedSubtitle?.language,
                            ),
                        )
                        DownloadNotifier.notifyCompleted(item.id, item.title)
                        break
                    } else {
                        repository.upsert(
                            item.copy(
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
                DownloadNotifier.notifyFailed(
                    id = item.id,
                    title = item.title,
                    error = e.message ?: "Download failed",
                )
                repository.upsert(
                    item.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = e.message ?: "Download failed",
                    ),
                )
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
                } else if (handle != null && handle.isValid) {
                    val info = handle.torrentFile()
                    if (info != null && info.isValid) {
                        val files = info.files()
                        val priorities = Array(info.numFiles()) { idx ->
                            val name = files.fileName(idx).lowercase()
                            when {
                                idx in wantedSet -> Priority.DEFAULT
                                name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") -> Priority.DEFAULT
                                else -> Priority.IGNORE
                            }
                        }
                        handle.prioritizeFiles(priorities)
                    }
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
                } else if (handle != null && handle.isValid) {
                    val info = handle.torrentFile()
                    if (info != null && info.isValid) {
                        val files = info.files()
                        val activeWanted = wantedSet.orEmpty()
                        val priorities = Array(info.numFiles()) { idx ->
                            val name = files.fileName(idx).lowercase()
                            when {
                                idx in activeWanted -> Priority.DEFAULT
                                name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") -> Priority.DEFAULT
                                else -> Priority.IGNORE
                            }
                        }
                        handle.prioritizeFiles(priorities)
                    }
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
                        val s = item.season ?: 1
                        val ep1 = Regex("""(?i)\bs0*${s}[.\-_ ]*e0*${item.episode}\b""")
                        val ep2 = Regex("""(?i)\b${s}x0*${item.episode}\b""")
                        val ep3 = Regex("""(?i)\bep?0*${item.episode}\b""")
                        val match = videos.firstOrNull { ep1.containsMatchIn(it.name) || ep2.containsMatchIn(it.name) }
                            ?: videos.firstOrNull { ep3.containsMatchIn(it.name) }
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
