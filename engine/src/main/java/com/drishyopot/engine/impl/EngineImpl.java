package com.drishyopot.engine.impl;

import com.drishyopot.engine.api.Engine;
import com.drishyopot.engine.api.EngineException;
import com.drishyopot.engine.api.HttpBridge;
import com.drishyopot.engine.api.PoTokenBridge;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** The engine backed by NewPipe Extractor. */
public final class EngineImpl implements Engine {
    private static final int MAX_PLAYLIST_PAGES = 6;
    private boolean ready;

    @Override
    public String version() {
        String v = "unknown";
        try (InputStream in = EngineImpl.class.getResourceAsStream("/engine.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                v = p.getProperty("newpipe", v);
            }
        } catch (Exception ignored) {
            // keep "unknown"
        }
        return "NewPipe Extractor " + v;
    }

    @Override
    public synchronized void init(HttpBridge http, PoTokenBridge poToken) throws EngineException {
        try {
            NewPipe.init(new BridgeDownloader(http), Localization.DEFAULT);
            YoutubeStreamExtractor.setPoTokenProvider(new BridgePoTokenProvider(poToken));
            ready = true;
        } catch (Throwable t) {
            throw ErrorKinds.wrap(t);
        }
    }

    @Override
    public String resolveVideo(String videoId) throws EngineException {
        requireReady();
        try {
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=" + videoId);
            return JsonOut.write(toMap(videoId, info));
        } catch (Throwable t) {
            throw ErrorKinds.wrap(t);
        }
    }

    @Override
    public String streamProfile(String url) {
        Map<String, Object> headers = new LinkedHashMap<>();
        String userAgent = null;
        try {
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                userAgent = YoutubeParsingHelper.getVisionOsUserAgent(null);
            }
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                headers.put("Origin", "https://www.youtube.com");
                headers.put("Referer", "https://www.youtube.com");
                headers.put("Sec-Fetch-Dest", "empty");
                headers.put("Sec-Fetch-Mode", "cors");
                headers.put("Sec-Fetch-Site", "cross-site");
            }
        } catch (Throwable ignored) {
            // fall back to the plain profile
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userAgent", userAgent);
        out.put("headers", headers);
        return JsonOut.write(out);
    }

    @Override
    public String loadPlaylist(String playlistUrl, int maxItems) throws EngineException {
        requireReady();
        try {
            PlaylistInfo info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl);
            List<Object> items = new ArrayList<>();
            addItems(items, info.getRelatedItems());
            Page page = info.getNextPage();
            int pages = 1;
            while (page != null && items.size() < maxItems && pages < MAX_PLAYLIST_PAGES) {
                org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<StreamInfoItem> next =
                        PlaylistInfo.getMoreItems(ServiceList.YouTube, playlistUrl, page);
                addItems(items, next.getItems());
                page = next.getNextPage();
                pages++;
            }
            boolean truncated = items.size() > maxItems || page != null;
            if (items.size() > maxItems) items = new ArrayList<>(items.subList(0, maxItems));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", info.getName());
            out.put("items", items);
            out.put("truncated", truncated);
            return JsonOut.write(out);
        } catch (Throwable t) {
            throw ErrorKinds.wrap(t);
        }
    }

    private void requireReady() throws EngineException {
        if (!ready) throw new EngineException(EngineException.OTHER, "Engine not initialised");
    }

    private static void addItems(List<Object> out, List<StreamInfoItem> items) {
        for (StreamInfoItem item : items) {
            String id = ErrorKinds.videoId(item.getUrl());
            if (id == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", item.getName());
            m.put("uploader", item.getUploaderName());
            m.put("durationSec", item.getDuration());
            out.add(m);
        }
    }

    // ---- Video JSON ----------------------------------------------------------------------------------

    private static Map<String, Object> toMap(String id, StreamInfo info) {
        boolean live = info.getStreamType() == StreamType.LIVE_STREAM
                || info.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", info.getName());
        m.put("uploader", info.getUploaderName());
        m.put("durationMs", info.getDuration() * 1000L);
        m.put("live", live);
        m.put("thumbnail", info.getThumbnails().isEmpty() ? null : info.getThumbnails().get(0).getUrl());
        String hls = info.getHlsUrl();
        m.put("hlsUrl", hls == null || hls.isEmpty() ? null : hls);
        putExtras(m, info);

        List<Object> videoOnly = new ArrayList<>();
        for (VideoStream s : info.getVideoOnlyStreams()) if (s.isUrl()) videoOnly.add(video(s));
        List<Object> muxed = new ArrayList<>();
        for (VideoStream s : info.getVideoStreams()) if (s.isUrl()) muxed.add(video(s));
        List<Object> audio = new ArrayList<>();
        for (AudioStream s : info.getAudioStreams()) if (s.isUrl()) audio.add(audio(s));
        List<Object> subs = new ArrayList<>();
        for (SubtitlesStream s : info.getSubtitles()) if (s.isUrl()) subs.add(subtitle(s));
        m.put("videoOnly", videoOnly);
        m.put("muxed", muxed);
        m.put("audio", audio);
        m.put("subtitles", subs);
        return m;
    }

    /** Channel, date, description, counts and chapters. Each is optional: one failing must not break playback. */
    private static void putExtras(Map<String, Object> m, StreamInfo info) {
        try { m.put("uploaderAvatar", firstImage(info.getUploaderAvatars())); } catch (Throwable ignored) { }
        try { m.put("uploaderUrl", info.getUploaderUrl()); } catch (Throwable ignored) { }
        try { m.put("subscribers", info.getUploaderSubscriberCount()); } catch (Throwable ignored) { }
        try {
            DateWrapper d = info.getUploadDate();
            m.put("uploadedAt", d == null ? 0L : d.offsetDateTime().toInstant().toEpochMilli());
        } catch (Throwable ignored) { }
        try {
            Description d = info.getDescription();
            m.put("description", d == null ? "" : d.getContent());
            m.put("descriptionHtml", d != null && d.getType() == Description.HTML);
        } catch (Throwable ignored) { }
        try { m.put("likes", info.getLikeCount()); } catch (Throwable ignored) { }
        try { m.put("views", info.getViewCount()); } catch (Throwable ignored) { }
        try {
            List<Object> chapters = new ArrayList<>();
            for (StreamSegment seg : info.getStreamSegments()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("title", seg.getTitle());
                c.put("startSec", seg.getStartTimeSeconds());
                c.put("preview", seg.getPreviewUrl());
                chapters.add(c);
            }
            m.put("chapters", chapters);
        } catch (Throwable ignored) { }
        try {
            List<Object> related = new ArrayList<>();
            for (org.schabi.newpipe.extractor.InfoItem item : info.getRelatedItems()) {
                if (!(item instanceof StreamInfoItem)) continue;
                StreamInfoItem v = (StreamInfoItem) item;
                String rid = ErrorKinds.videoId(v.getUrl());
                if (rid == null) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", rid);
                r.put("title", v.getName());
                r.put("uploader", v.getUploaderName());
                r.put("durationSec", v.getDuration());
                r.put("views", v.getViewCount());
                r.put("date", v.getTextualUploadDate());
                r.put("thumbnail", firstImage(v.getThumbnails()));
                related.add(r);
                if (related.size() >= 30) break;
            }
            m.put("related", related);
        } catch (Throwable ignored) { }
    }

    private static String firstImage(List<Image> images) {
        return images == null || images.isEmpty() ? null : images.get(0).getUrl();
    }

    @Override
    public String comments(String videoId, int max) throws EngineException {
        requireReady();
        try {
            CommentsInfo info = CommentsInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=" + videoId);
            Map<String, Object> out = new LinkedHashMap<>();
            boolean disabled = false;
            try { disabled = info.isCommentsDisabled(); } catch (Throwable ignored) { }
            long count = -1;
            // The total is not a public getter in every extractor version, so look for it by name.
            for (String name : new String[] {"getCommentCount", "getCommentsCount"}) {
                try {
                    Object v = info.getClass().getMethod(name).invoke(info);
                    if (v instanceof Number) { count = ((Number) v).longValue(); break; }
                } catch (Throwable ignored) { }
            }
            out.put("disabled", disabled);
            out.put("count", count);
            List<Object> items = new ArrayList<>();
            for (CommentsInfoItem c : info.getRelatedItems()) {
                if (items.size() >= max) break;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("author", c.getUploaderName());
                m.put("avatar", firstImage(c.getUploaderAvatars()));
                Description text = c.getCommentText();
                m.put("text", text == null ? "" : text.getContent());
                m.put("likes", (long) c.getLikeCount());
                m.put("date", c.getTextualUploadDate());
                m.put("pinned", c.isPinned());
                items.add(m);
            }
            out.put("items", items);
            return JsonOut.write(out);
        } catch (Throwable t) {
            throw ErrorKinds.wrap(t);
        }
    }

    private static long[] ranges(ItagItem item) {
        if (item == null) return new long[] {0, 0, 0, 0};
        return new long[] {item.getInitStart(), item.getInitEnd(), item.getIndexStart(), item.getIndexEnd()};
    }

    private static long contentLength(ItagItem item) {
        return item == null ? 0L : item.getContentLength();
    }

    private static Map<String, Object> video(VideoStream s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentLength", contentLength(s.getItagItem()));
        m.put("url", s.getContent());
        m.put("height", s.getHeight());
        m.put("width", s.getWidth());
        m.put("fps", s.getFps());
        m.put("codec", s.getCodec());
        m.put("bitrate", s.getBitrate());
        m.put("mime", s.getFormat() == null ? "video/mp4" : s.getFormat().getMimeType());
        m.put("ranges", ranges(s.getItagItem()));
        return m;
    }

    private static Map<String, Object> audio(AudioStream s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentLength", contentLength(s.getItagItem()));
        m.put("url", s.getContent());
        m.put("trackId", s.getAudioTrackId());
        m.put("label", audioLabel(s));
        m.put("kind", kind(s.getAudioTrackType()));
        m.put("language", s.getAudioLocale() == null ? null : s.getAudioLocale().toLanguageTag());
        m.put("bitrate", s.getAverageBitrate());
        m.put("codec", s.getCodec());
        m.put("mime", s.getFormat() == null ? "audio/mp4" : s.getFormat().getMimeType());
        m.put("ranges", ranges(s.getItagItem()));
        return m;
    }

    private static String kind(AudioTrackType t) {
        if (t == AudioTrackType.ORIGINAL) return "ORIGINAL";
        if (t == AudioTrackType.DUBBED) return "DUBBED";
        if (t == AudioTrackType.DESCRIPTIVE) return "DESCRIPTIVE";
        return "OTHER";
    }

    private static String audioLabel(AudioStream s) {
        String language = s.getAudioLocale() == null ? null : s.getAudioLocale().getDisplayLanguage();
        if (language != null && language.isEmpty()) language = null;
        String base = language != null ? language : s.getAudioTrackName();
        String kind = null;
        if (s.getAudioTrackType() == AudioTrackType.ORIGINAL) kind = "original";
        else if (s.getAudioTrackType() == AudioTrackType.DUBBED) kind = "dubbed";
        else if (s.getAudioTrackType() == AudioTrackType.DESCRIPTIVE) kind = "descriptive";
        if (base == null && kind == null) return "Default";
        if (base == null) return kind;
        return kind == null ? base : base + " \u00b7 " + kind;
    }

    private static Map<String, Object> subtitle(SubtitlesStream s) {
        String mime = s.getFormat() == null ? null : s.getFormat().getMimeType();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", s.getContent());
        m.put("language", s.getLanguageTag() == null ? "und" : s.getLanguageTag());
        m.put("label", s.getDisplayLanguageName() + (s.isAutoGenerated() ? " (auto)" : ""));
        m.put("mime", subtitleMime(mime));
        m.put("auto", s.isAutoGenerated());
        return m;
    }

    private static String subtitleMime(String mime) {
        if (mime == null) return "text/vtt";
        String l = mime.toLowerCase();
        if (l.contains("vtt")) return "text/vtt";
        if (l.contains("ttml") || l.contains("xml")) return "application/ttml+xml";
        if (l.contains("srt") || l.contains("subrip")) return "application/x-subrip";
        return mime;
    }
}
