package io.github.hatake716.dopagaki

import android.content.Context

/** 比率と各ペインの最後の URL の保存（SPEC.md §4, §5） */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("dopagaki", Context.MODE_PRIVATE)

    var topRatio: Float
        get() = prefs.getFloat(KEY_RATIO, DEFAULT_RATIO)
        set(value) = prefs.edit().putFloat(KEY_RATIO, value).apply()

    var youtubeUrl: String
        get() = prefs.getString(KEY_YOUTUBE_URL, DEFAULT_YOUTUBE_URL) ?: DEFAULT_YOUTUBE_URL
        set(value) = prefs.edit().putString(KEY_YOUTUBE_URL, value).apply()

    var xUrl: String
        get() = prefs.getString(KEY_X_URL, DEFAULT_X_URL) ?: DEFAULT_X_URL
        set(value) = prefs.edit().putString(KEY_X_URL, value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    companion object {
        const val DEFAULT_RATIO = 1f / 3f
        const val MIN_RATIO = 0.15f
        const val MAX_RATIO = 0.85f
        const val DEFAULT_YOUTUBE_URL = "https://m.youtube.com/"
        const val DEFAULT_X_URL = "https://x.com/home"

        private const val KEY_RATIO = "top_ratio"
        private const val KEY_YOUTUBE_URL = "youtube_url"
        private const val KEY_X_URL = "x_url"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
