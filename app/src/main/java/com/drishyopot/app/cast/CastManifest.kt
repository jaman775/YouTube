package com.drishyopot.app.cast

import com.drishyopot.app.yt.MediaRanges

/** One DASH representation served by the local proxy. */
data class DashRep(
    val id: String,
    val mimeType: String,
    val codecs: String,
    val bandwidth: Int,
    val url: String,
    val ranges: MediaRanges,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
)

/**
 * Builds a static on-demand DASH manifest (ISO BMFF, sidx-indexed) that points at the proxy's
 * stream URLs. Chromecast's Default Media Receiver reads the segment index itself.
 */
object CastManifest {
    fun build(video: DashRep, audio: DashRep, durationMs: Long): String {
        val seconds = "%.3f".format(java.util.Locale.US, durationMs / 1000.0)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" """)
            append("""type="static" mediaPresentationDuration="PT${seconds}S" minBufferTime="PT2S">""").append('\n')
            append("<Period>\n")
            appendSet(video, "video")
            appendSet(audio, "audio")
            append("</Period>\n</MPD>\n")
        }
    }

    private fun StringBuilder.appendSet(rep: DashRep, kind: String) {
        append("""<AdaptationSet mimeType="${xml(rep.mimeType)}" contentType="$kind" segmentAlignment="true" startWithSAP="1">""").append('\n')
        append("""<Representation id="${xml(rep.id)}" codecs="${xml(rep.codecs)}" bandwidth="${rep.bandwidth}"""")
        if (kind == "video") {
            if (rep.width > 0) append(""" width="${rep.width}"""")
            if (rep.height > 0) append(""" height="${rep.height}"""")
            if (rep.fps > 0) append(""" frameRate="${rep.fps}"""")
        }
        append(">\n")
        append("<BaseURL>${xml(rep.url)}</BaseURL>\n")
        append("""<SegmentBase indexRange="${rep.ranges.indexStart}-${rep.ranges.indexEnd}">""")
        append("""<Initialization range="${rep.ranges.initStart}-${rep.ranges.initEnd}"/></SegmentBase>""").append('\n')
        append("</Representation>\n</AdaptationSet>\n")
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** Parsed HTTP `Range: bytes=a-b` header, clamped to a known total length. */
data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1

    companion object {
        /** Returns null when the header is absent or unusable (serve the whole file). */
        fun parse(header: String?, total: Long): ByteRange? {
            if (header == null || total <= 0) return null
            val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() } ?: return null
            val first = spec.substringBefore(',').trim()
            val startText = first.substringBefore('-', "")
            val endText = first.substringAfter('-', "")
            val start: Long
            val end: Long
            if (startText.isEmpty()) {
                // Suffix range: the last N bytes.
                val n = endText.toLongOrNull() ?: return null
                if (n <= 0) return null
                start = (total - n).coerceAtLeast(0)
                end = total - 1
            } else {
                start = startText.toLongOrNull() ?: return null
                end = if (endText.isEmpty()) total - 1 else (endText.toLongOrNull() ?: return null).coerceAtMost(total - 1)
            }
            if (start < 0 || start > end || start >= total) return null
            return ByteRange(start, end)
        }
    }
}
