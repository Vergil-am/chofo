package dev.dogetime.ui.components

/** A size for people: "512 KB", "3.4 MB", "1.25 GB". */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${(bytes / 1024.0).toInt()} KB"
    bytes < 1024L * 1024L * 1024L -> "${(bytes / (1024.0 * 1024.0)).format(1)} MB"
    else -> "${(bytes / (1024.0 * 1024.0 * 1024.0)).format(2)} GB"
}

/** A download speed for people: "340 KB/s", "2.5 MB/s". */
internal fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec <= 0L -> "0 KB/s"
    bytesPerSec < 1024L * 1024L -> "${(bytesPerSec / 1024.0).toInt()} KB/s"
    else -> "${(bytesPerSec / (1024.0 * 1024.0)).format(1)} MB/s"
}

private fun Double.format(decimals: Int): String {
    val factor = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * factor).toLong() / factor
    return rounded.toString()
}
