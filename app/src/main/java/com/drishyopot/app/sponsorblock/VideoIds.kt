package com.drishyopot.app.sponsorblock

import androidx.media3.common.MediaItem

object VideoIds {
    const val EXTRA_KEY = "sb_video_id"

    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val urlPatterns = listOf(
        Regex("""(?:youtube\.com|youtube-nocookie\.com)/(?:watch\?(?:[^#]*&)?v=|embed/|shorts/|live/|v/)([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    )

    fun isValid(id: String?): Boolean = id != null && idPattern.matches(id)

    /** Explicit ID stored with the item wins; otherwise the ID is parsed from a YouTube URL. */
    fun extract(item: MediaItem?): String? {
        item ?: return null
        item.mediaMetadata.extras?.getString(EXTRA_KEY)?.takeIf { isValid(it) }?.let { return it }
        val url = item.mediaId
        return urlPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
    }
}
