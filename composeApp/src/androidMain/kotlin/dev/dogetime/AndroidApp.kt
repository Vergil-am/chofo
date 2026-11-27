package dev.dogetime

import `is`.xyz.mpv.Utils
import android.content.Context
import android.util.Log

private const val TAG = "DogetimeApp"

/** Holds the application context for shared code that needs it, such as the database. */
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        this.context = app
        try {
            Utils.copyAssets(app)
        } catch (e: Exception) {
            Log.w(TAG, "Copying the mpv assets failed", e)
        }
    }
}
