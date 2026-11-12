package dev.dogetime.torrent

import android.os.Environment
import dev.dogetime.AndroidApp
import java.io.File

internal actual fun torrentDataDir(): File = File(AndroidApp.context.cacheDir, "torrents")

internal actual fun torrentDownloadsDir(): File {
    val externalDownloads = AndroidApp.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val base = externalDownloads ?: AndroidApp.context.filesDir
    return File(base, "downloads").apply { mkdirs() }
}

// The library is packaged in the APK's lib/<abi>/ folder, where System.loadLibrary finds it.
internal actual fun prepareTorrentLibrary() = Unit
