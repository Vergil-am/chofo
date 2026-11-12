package dev.dogetime.data

import dev.dogetime.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)

data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        fun parse(raw: String): SemVer {
            val clean = raw.trim().removePrefix("v").removePrefix("V")
            val digits = clean.takeWhile { it.isDigit() || it == '.' }
            val parts = digits.split('.').mapNotNull { it.toIntOrNull() }
            return SemVer(
                major = parts.getOrNull(0) ?: 0,
                minor = parts.getOrNull(1) ?: 0,
                patch = parts.getOrNull(2) ?: 0,
            )
        }
    }
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String?,
        val releaseUrl: String,
        val downloadUrl: String,
        val publishedAt: String? = null,
    ) : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

class UpdateChecker(private val httpClient: HttpClient) {

    suspend fun checkForUpdates(currentVersion: String = AppConfig.APP_VERSION): UpdateStatus {
        return try {
            val release = fetchLatestRelease() ?: return UpdateStatus.UpToDate(currentVersion)
            val latestSemVer = SemVer.parse(release.tagName)
            val currentSemVer = SemVer.parse(currentVersion)

            if (latestSemVer > currentSemVer) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val downloadUrl = apkAsset?.browserDownloadUrl
                    ?: release.assets.firstOrNull()?.browserDownloadUrl
                    ?: release.htmlUrl

                UpdateStatus.UpdateAvailable(
                    latestVersion = release.tagName,
                    releaseName = release.name?.ifBlank { release.tagName } ?: release.tagName,
                    releaseNotes = release.body,
                    releaseUrl = release.htmlUrl,
                    downloadUrl = downloadUrl,
                    publishedAt = release.publishedAt,
                )
            } else {
                UpdateStatus.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateStatus.Error(e.message ?: "Failed to check for updates")
        }
    }

    private suspend fun fetchLatestRelease(): GitHubRelease? {
        val repo = AppConfig.RELEASES_REPO
        val latestUrl = "https://api.github.com/repos/$repo/releases/latest"
        val releasesUrl = "https://api.github.com/repos/$repo/releases"

        // 1. Try /releases/latest
        try {
            return httpClient.get(latestUrl) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Chofo-App/${AppConfig.APP_VERSION}")
            }.body<GitHubRelease>()
        } catch (_: Exception) {
            // Ignore and fallback to list
        }

        // 2. Fallback to /releases
        try {
            val list = httpClient.get(releasesUrl) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Chofo-App/${AppConfig.APP_VERSION}")
            }.body<List<GitHubRelease>>()
            return list.firstOrNull { !it.draft && !it.prerelease } ?: list.firstOrNull { !it.draft }
        } catch (_: Exception) {
            // Both failed
        }

        return null
    }
}
