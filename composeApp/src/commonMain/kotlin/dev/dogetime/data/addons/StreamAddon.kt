package dev.dogetime.data.addons

import dev.dogetime.domain.WatchTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A Stremio add-on that lists streams, installed from its `manifest.json` URL. Requests go to
 * `[baseUrl]/stream/{type}/{id}.json` (see https://github.com/Stremio/stremio-addon-sdk).
 */
@Serializable
data class StreamAddon(
    /** The manifest's `id`, e.g. `com.stremio.torrentio.addon`. Installing it again replaces it. */
    val id: String,
    val name: String,
    /** The manifest URL without `/manifest.json`. Configured add-ons keep their settings in it. */
    val baseUrl: String,
    val version: String = "",
    val description: String = "",
    /** Content types the stream resource serves, e.g. `movie`, `series`, `anime`. */
    val types: List<String> = emptyList(),
    /** ID prefixes it understands, e.g. `tt` (IMDb) and `kitsu`. Null: any. */
    val idPrefixes: List<String>? = null,
    /** Whether it has a configuration page (`{baseUrl}/configure`). */
    val configurable: Boolean = false,
) {
    val manifestUrl: String get() = "$baseUrl/manifest.json"
    val configureUrl: String? get() = if (configurable) "$baseUrl/configure" else null

    /** The stream path for [target], or null when this add-on can't serve it. Kitsu wins for anime. */
    fun streamPath(target: WatchTarget): String? {
        val type = when (target) {
            is WatchTarget.Movie -> "movie"
            is WatchTarget.Episode -> "series"
        }
        val kitsu = target.kitsuId
        if (kitsu != null && handles("kitsu", type, anime = true)) {
            return when (target) {
                is WatchTarget.Movie -> "movie/kitsu:$kitsu.json"
                is WatchTarget.Episode -> "series/kitsu:$kitsu:${target.episode}.json"
            }
        }
        val imdb = target.imdbId
        if (imdb != null && handles("tt", type, anime = kitsu != null)) {
            return when (target) {
                is WatchTarget.Movie -> "movie/$imdb.json"
                is WatchTarget.Episode -> "series/$imdb:${target.season}:${target.episode}.json"
            }
        }
        return null
    }

    fun canServe(target: WatchTarget): Boolean = streamPath(target) != null

    private fun handles(prefix: String, type: String, anime: Boolean): Boolean {
        val prefixOk = idPrefixes?.any { prefix.startsWith(it) || it.startsWith(prefix) } ?: true
        val typeOk = types.isEmpty() || type in types || (anime && "anime" in types)
        return prefixOk && typeOk
    }

    companion object {
        /** Preinstalled on first launch; removable like any other. The same providers as before. */
        val Defaults = listOf(
            StreamAddon(
                id = "com.stremio.torrentio.addon",
                name = "Torrentio",
                baseUrl = "https://torrentio.strem.fun",
                types = listOf("movie", "series", "anime"),
                idPrefixes = listOf("tt", "kitsu"),
                configurable = true,
            ),
            StreamAddon(
                id = "dev.chofo.default.meteor",
                name = "Meteor",
                baseUrl = "https://meteorfortheweebs.midnightignite.me/eyJjYWNoZWRPbmx5IjpmYWxzZSwic2tpcFJlbGVhc2VGaWx0ZXIiOnRydWUsInJlbW92ZVRyYXNoIjpmYWxzZSwicmVtb3ZlU2FtcGxlcyI6ZmFsc2UsImFsbG93QWR1bHQiOnRydWUsImV4Y2x1ZGUzRCI6ZmFsc2UsImVuYWJsZVNlYURleCI6ZmFsc2UsInNob3dZb3VyTWVkaWEiOmZhbHNlLCJ5b3VyTWVkaWFMZWdhY3lNb2RlIjpmYWxzZSwibWluU2VlZGVycyI6MCwibWF4UmVzdWx0cyI6MCwibWF4UGVyUmVzb2x1dGlvbiI6MCwicmVzb2x1dGlvbnMiOltdLCJwcmVmZXJyZWRMYW5ncyI6WyJlbiIsIm11bHRpIiwiYXIiXSwibGFuZ3VhZ2VzIjpbXSwiZXhjbHVkZWRMYW5ncyI6W10sInJlc3VsdEZvcm1hdCI6WyJ0aXRsZSIsInF1YWxpdHkiLCJzaXplIiwiYXVkaW8iLCJzdWJsYW5nIiwic2VlZGVycyJdLCJsYW5ndWFnZUZvcm1hdCI6ImZsYWdzIiwic29ydE9yZGVyIjpbInNlZWRlcnMiLCJjYWNoZWQiLCJzZWFkZXgiLCJyZXNvbHV0aW9uIiwicXVhbGl0eSIsImxhbmd1YWdlIiwic2l6ZSIsInBhY2siLCJoZHIiLCJjb2RlYyJdLCJhbGxvd1AyUCI6dHJ1ZSwiZXhjbHVkZWRTb3VyY2VzIjpbXX0",
                types = listOf("movie", "series", "anime"),
                idPrefixes = listOf("tt", "kitsu"),
            ),
            StreamAddon(
                id = "dev.chofo.default.mediafusion",
                name = "MediaFusion",
                baseUrl = "https://mediafusionfortheweebs.midnightignite.me/D-HUufCDQWU_edY4p0pvlv3bW-kT1_rjAObwDiaW_gVmYS65NP87uSq5iujP9JDcc7m82or13NlaPFYRxXednWPW-fXFvfmHK5H0QXZosKEz8F5iuzsMQsnqKo6WVN8ZCuoGSjpK7KyjWXF2TTXQ0bQx1ILQ5xQdDAxrP-dhLogEO0ssOEJbemPch-nb_KkmeEjKOJil4MmD2XCnNPDO4_I6qI9hmbukBaTx4yCwThh1iu-SDoMpI8U9RDD7OWebbptqEtmggHAP8LQjIsQAWdqwkPfmlAD0R6K46zOY3Ow2dekFZsZ65exLgm5704CjV3srnje4i084YBL1bVUvAgkHjvrLiNY4zdtl60_4o6M2D2q9PIGkGsqUrTDWzbHvrugNTFt8_HooB3svJAXp2pgjXOdNv0xuYW26_iEDxy5ksvxUkUqlGAOjG9-GmQHprwxQ3I5HSe7vgETrz-DV1e8erToAp4eAgqS2LBJBqrJkn8KaW8dndbtA8_X_86hWQUmz5M3TDGW71SvLeh15ZXNfcvNU9DbfMhM4wF1_-5mNAi20-be5rCYIAocmvFv4Q7R6KugmokEsic_Zvpv_tdmLhWRwSO-uSGKgAja-n44kuUZoca1UeqCQhry7sJer_85ezXpnQdVXL0Is4e3KkdqrNtNOhNqPyWbOyFMbmxLLiHegALwgL1QRWMlxpmCcnSZgFnARYs4rVMn15t620EfbHWGypLlnhOivVIbxfCHsZv-QuSoeM4_LIHYLZMvT9psePaDMHrCupmONjdpqCApYY5Bha4g_oc2Ip0uEPYdXpuxSc2Iw8_8FbBw2OJRF3gWX65pqypmzspqa0Hki-s6Bslc1PLAjmhr8u4VSxJShU8mFokj_shYTdQyjYGfzn1lPA8oSBJUprTMnG8ynDQdB9jpcAtIH3Nfnp9n3dxgv6QmVW9aC4R-fTcnrHeVV40sIw8BNu9ype1Kt7SMLdeP97Q0gYSc4m_r46Z2EC1_WBbJaApjmln8oMdahDTzxMRVrPAOPSe8bEtED2Qs1jxX5pzs6Qa0MzfOLRRPvLX33JDFybOnvFm6rwrRVRFW_UsgNEtSzdgymFMH04m1aQFCzrNk5btYa8yr5VqzolkT5PNIkjjDCGiuPkhVCAPDx7q_ranU5xDl4wNbvr7KHgYSEErRX0iuHgtvb_OkOBRPUAUcvmsWAZvHwyVtZMxYePbjGwnUqVjsyjL3rcUTl0HCzbFsuObFAy-AYFRhlPj-JZQijvcoYjEPPnLeZolIThCrnfFInc-qijhRY0wECVujyscUI0Y4-nb5PUF4H_IJMiXeSqF0a2MefAwHgtgAIegfnkWaglgnypEc7kvzfZtFn8P-MfzA8RGCZr6ZtNAJPLRd6H9asrg4-BeuKCPPS8_e9Vz-3EfusaIDf3ynIeIBx967UlosPcl-44IHPAORasIrU22r74Oa-AIA5rPCuQFGsgD93A4e_oyKDALNxaiPg8yp4FlZDvpUtMd8iC0yDmAb9OJyx_NtLfsOselZpWbm9HfKkukewQdnqywLRl9tnVH1P70P3_1tICtv8z-p8Q",
                types = listOf("movie", "series", "anime"),
                idPrefixes = listOf("tt", "kitsu"),
            ),
            StreamAddon(
                id = "dev.chofo.default.comet",
                name = "Comet",
                baseUrl = "https://comet.feels.legal/eyJtYXhSZXN1bHRzUGVyUmVzb2x1dGlvbiI6MTAsIm1heFNpemUiOjIxNDc0ODM2NDgwLCJjYWNoZWRPbmx5IjpmYWxzZSwic29ydENhY2hlZFVuY2FjaGVkVG9nZXRoZXIiOmZhbHNlLCJyZW1vdmVUcmFzaCI6dHJ1ZSwicmVzdWx0Rm9ybWF0IjpbImFsbCJdLCJkZWJyaWRTZXJ2aWNlcyI6W10sImVuYWJsZVRvcnJlbnQiOnRydWUsImRlZHVwbGljYXRlU3RyZWFtcyI6ZmFsc2UsInNjcmFwZURlYnJpZEFjY291bnRUb3JyZW50cyI6ZmFsc2UsImRlYnJpZFN0cmVhbVByb3h5UGFzc3dvcmQiOiIiLCJsYW5ndWFnZXMiOnsicmVxdWlyZWQiOltdLCJhbGxvd2VkIjpbXSwiZXhjbHVkZSI6W10sInByZWZlcnJlZCI6W119LCJyZXNvbHV0aW9ucyI6eyJyMjE2MHAiOmZhbHNlfSwib3B0aW9ucyI6eyJyZW1vdmVfcmFua3NfdW5kZXIiOi0xMDAwMDAwMDAwMCwiYWxsb3dfZW5nbGlzaF9pbl9sYW5ndWFnZXMiOmZhbHNlLCJyZW1vdmVfdW5rbm93bl9sYW5ndWFnZXMiOmZhbHNlfX0=",
                types = listOf("movie", "series", "anime"),
                idPrefixes = listOf("tt", "kitsu"),
            ),
        )
    }
}

/** Why an add-on couldn't be installed, for the user. */
class AddonException(message: String) : Exception(message)

/**
 * The manifest URL's base, from what the user pasted: a `https://…/manifest.json` link, the
 * same without `/manifest.json`, or a `stremio://` install link.
 */
fun addonBaseUrl(input: String): String {
    var url = input.trim()
    if (url.startsWith("stremio://", ignoreCase = true)) url = "https://" + url.substring("stremio://".length)
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
        throw AddonException("Paste the add-on's manifest link, starting with https:// or stremio://.")
    }
    url = url.substringBefore('#').trimEnd('/')
    if (url.endsWith("/manifest.json", ignoreCase = true)) url = url.dropLast("/manifest.json".length)
    if (url.substringAfter("://").isBlank()) throw AddonException("That link has no address.")
    return url.trimEnd('/')
}

/** Reads a manifest's JSON into a [StreamAddon], or explains why it isn't a stream add-on. */
fun parseStreamManifest(text: String, baseUrl: String): StreamAddon {
    val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        ?: throw AddonException("That link doesn't return an add-on manifest.")
    val id = root.string("id") ?: throw AddonException("The manifest has no id.")
    val name = root.string("name") ?: id
    val topTypes = root.strings("types")
    val topPrefixes = root.stringsOrNull("idPrefixes")

    // `resources` mixes plain names and objects that override types and idPrefixes.
    val stream = (root["resources"] as? JsonArray)?.firstNotNullOfOrNull { resource ->
        when {
            resource is JsonPrimitive && resource.contentOrNull == "stream" -> topTypes to topPrefixes
            resource is JsonObject && resource.string("name") == "stream" ->
                (resource.stringsOrNull("types") ?: topTypes) to (resource.stringsOrNull("idPrefixes") ?: topPrefixes)
            else -> null
        }
    } ?: throw AddonException("$name doesn't provide streams, so Chofo can't use it yet.")

    val hints = root["behaviorHints"] as? JsonObject
    if (hints?.boolean("configurationRequired") == true && baseUrl.substringAfter("://").count { it == '/' } == 0) {
        throw AddonException("$name needs to be configured first. Open its configure page, then paste the link it gives you.")
    }
    return StreamAddon(
        id = id,
        name = name,
        baseUrl = baseUrl,
        version = root.string("version").orEmpty(),
        description = root.string("description").orEmpty(),
        types = stream.first,
        idPrefixes = stream.second,
        configurable = hints?.boolean("configurable") == true,
    )
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"
private fun JsonObject.strings(key: String): List<String> = stringsOrNull(key).orEmpty()
private fun JsonObject.stringsOrNull(key: String): List<String>? =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
