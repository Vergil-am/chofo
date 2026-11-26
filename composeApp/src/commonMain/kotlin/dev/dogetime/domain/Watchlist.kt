package dev.dogetime.domain

/** Where a title sits on the user's watchlist. [apiName] is the Chofo API's name for it. */
enum class WatchStatus(val label: String, val apiName: String) {
    Watching("Watching", "watching"),
    PlanToWatch("Plan to watch", "plan_to_watch"),
    OnHold("On hold", "on_hold"),
    Completed("Completed", "completed"),
    Dropped("Dropped", "dropped");

    companion object {
        fun fromApi(name: String): WatchStatus? = entries.firstOrNull { it.apiName == name }
    }
}

/** A title on the watchlist. */
data class WatchlistEntry(
    val item: MediaItem,
    val status: WatchStatus,
    val updatedAt: Long,
)
