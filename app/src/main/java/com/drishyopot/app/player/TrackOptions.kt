@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

data class TrackOption(
    val label: String,
    val group: Tracks.Group,
    val trackIndex: Int,
    val selected: Boolean,
)

object TrackOptions {
    fun videoHeights(tracks: Tracks): List<Int> =
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).height } }
            .filter { it > 0 }.distinct().sortedDescending()

    fun audio(tracks: Tracks): List<TrackOption> = options(tracks, C.TRACK_TYPE_AUDIO)

    fun text(tracks: Tracks): List<TrackOption> = options(tracks, C.TRACK_TYPE_TEXT)

    private fun options(tracks: Tracks, type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        tracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                out += TrackOption(label(g.getTrackFormat(i), type), g, i, g.isTrackSelected(i))
            }
        }
        return out
    }

    private fun label(f: Format, type: Int): String {
        val lang = f.language?.takeIf { it != C.LANGUAGE_UNDETERMINED }
            ?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
        val parts = listOfNotNull(
            f.label ?: lang,
            if (type == C.TRACK_TYPE_AUDIO && f.bitrate > 0) "${f.bitrate / 1000} kbps" else null,
            if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null,
        )
        return parts.joinToString(" · ").ifBlank { "#${f.id ?: "?"}" }
    }

    fun applyQuality(player: ExoPlayer, height: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (height <= 0) {
            b.clearVideoSizeConstraints()
        } else {
            b.setMinVideoSize(0, height).setMaxVideoSize(Int.MAX_VALUE, height)
        }
        player.trackSelectionParameters = b.build()
    }

    fun selectTrack(player: ExoPlayer, option: TrackOption) {
        val type = option.group.type
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun disableText(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
}
