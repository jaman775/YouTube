package com.drishyopot.app.sponsorblock

import androidx.annotation.StringRes
import com.drishyopot.app.R
import com.drishyopot.app.settings.PrefKeys

/** SponsorBlock segment categories, each tied to the settings toggle that enables skipping it. */
enum class SbCategory(
    val api: String,
    val prefKey: String,
    val color: Int,
    @StringRes val label: Int,
) {
    SPONSOR("sponsor", PrefKeys.SKIP_SPONSORS, 0xFF00D400.toInt(), R.string.sb_category_sponsor),
    SELF_PROMO("selfpromo", PrefKeys.SKIP_SELF_PROMO, 0xFFFFFF00.toInt(), R.string.sb_category_selfpromo),
    INTERACTION("interaction", PrefKeys.SKIP_INTERACTION, 0xFFCC00FF.toInt(), R.string.sb_category_interaction),
    INTRO("intro", PrefKeys.SKIP_INTRO, 0xFF00FFFF.toInt(), R.string.sb_category_intro),
    OUTRO("outro", PrefKeys.SKIP_OUTRO, 0xFF0202ED.toInt(), R.string.sb_category_outro),
    PREVIEW("preview", PrefKeys.SKIP_PREVIEW, 0xFF008FD6.toInt(), R.string.sb_category_preview),
    MUSIC_OFFTOPIC("music_offtopic", PrefKeys.SKIP_MUSIC_OFFTOPIC, 0xFFFF9900.toInt(), R.string.sb_category_music),
    FILLER("filler", PrefKeys.SKIP_FILLER, 0xFF7300FF.toInt(), R.string.sb_category_filler),
    HIGHLIGHT("poi_highlight", PrefKeys.SKIP_POI_HIGHLIGHT, 0xFFFF1684.toInt(), R.string.sb_category_highlight);

    companion object {
        fun fromApi(name: String): SbCategory? = entries.firstOrNull { it.api == name }
    }
}

object SbAction {
    const val SKIP = "skip"
    const val MUTE = "mute"
    const val POI = "poi"
}

data class SponsorSegment(
    val uuid: String,
    val category: SbCategory,
    val actionType: String,
    val startMs: Long,
    val endMs: Long,
    val videoDurationMs: Long,
) {
    val isPoi: Boolean get() = actionType == SbAction.POI || category == SbCategory.HIGHLIGHT
}

/** A skip the engine is about to make, shown as a chip with a countdown. */
data class PendingSkip(val segment: SponsorSegment, val remainingMs: Long, val startedAt: Long)

data class SkipEvent(val id: Long, val category: SbCategory)

enum class SbStatus { IDLE, NO_ID, OFF, LOADING, READY, ERROR }
