package dev.dogetime.data.addons

import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The stream add-ons installed on this device, in the order they're shown. Starts with
 * [StreamAddon.Defaults]; after that the list is whatever the user made it.
 */
class StreamAddonRepository(
    private val prefs: PreferenceDao,
    private val client: HttpClient,
) {
    private val mutex = Mutex()

    val addons: Flow<List<StreamAddon>> = prefs.observe(KEY).map { decode(it) }

    suspend fun current(): List<StreamAddon> = decode(prefs.observe(KEY).first())

    /**
     * Fetches the manifest at [url] and installs it, at the end of the list. An add-on with the same
     * id is replaced in place, which is how a re-configured add-on gets its new settings.
     */
    suspend fun install(url: String): StreamAddon {
        val base = addonBaseUrl(url)
        val response = try {
            client.get("$base/manifest.json")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AddonException("Couldn't reach that add-on. Check the link and your connection.")
        }
        if (!response.status.isSuccess()) throw AddonException("The add-on answered ${response.status.value}. Check the link.")
        val addon = parseStreamManifest(response.bodyAsText(), base)
        mutex.withLock {
            val list = current().toMutableList()
            val existing = list.indexOfFirst { it.id == addon.id }
            if (existing >= 0) list[existing] = addon else list += addon
            save(list)
        }
        return addon
    }

    suspend fun remove(id: String) = mutex.withLock { save(current().filterNot { it.id == id }) }

    /** Moves the add-on one place up ([delta] = -1) or down (+1). */
    suspend fun move(id: String, delta: Int) = mutex.withLock {
        val list = current().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@withLock
        list.add(to, list.removeAt(from))
        save(list)
    }

    /** Puts back the preinstalled add-ons that were removed, keeping the rest. */
    suspend fun restoreDefaults() = mutex.withLock {
        val list = current()
        save(list + StreamAddon.Defaults.filter { d -> list.none { it.id == d.id } })
    }

    private suspend fun save(list: List<StreamAddon>) {
        prefs.upsert(PreferenceEntity(KEY, json.encodeToString(list)))
    }

    private fun decode(value: String?): List<StreamAddon> =
        if (value == null) StreamAddon.Defaults
        else runCatching { json.decodeFromString<List<StreamAddon>>(value) }.getOrDefault(StreamAddon.Defaults)

    private companion object {
        const val KEY = "stream_addons"
        val json = Json { ignoreUnknownKeys = true }
    }
}
