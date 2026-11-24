package dev.dogetime.data

import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.WatchTarget

/** Reports playback to a tracking service. Kept small so another service can be added later. */
interface Scrobbler {
    /** A session for one playback of [target], or null when it can't be tracked. */
    fun newSession(target: WatchTarget, item: MediaItem): ScrobbleSession?
}

/**
 * One playback. Calls are cheap and never block: the session decides what is worth sending.
 * Progress is a percentage, 0 to 100.
 */
interface ScrobbleSession {
    /** The current position. Never sent by itself; used by the next real event. */
    fun onProgress(percent: Double)

    /** The user played or paused. */
    fun onPlayingChanged(isPlaying: Boolean, percent: Double)

    /** Playback ended or the player closed. Later calls are ignored. */
    fun onFinished(percent: Double)
}
