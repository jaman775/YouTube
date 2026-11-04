package com.drishyopot.engine.api;

/**
 * The YouTube engine: everything that has to change when YouTube changes. The app ships one copy and
 * can replace it with a signed update downloaded at runtime, so this interface must stay small and
 * stable. Results cross the boundary as JSON strings so no engine classes leak into the app.
 */
public interface Engine {
    /** Bump only when this interface changes; engines built for another version are ignored. */
    int API_VERSION = 3;

    /** Human readable, e.g. "NewPipe Extractor v0.26.5". */
    String version();

    /** Called once after loading. */
    void init(HttpBridge http, PoTokenBridge poToken) throws EngineException;

    /**
     * Resolves a video. JSON schema:
     * {id,title,uploader,durationMs,live,thumbnail,hlsUrl,
     *  videoOnly:[{url,height,fps,codec,bitrate,width,mime,contentLength,ranges:[initStart,initEnd,indexStart,indexEnd]}],
     *  muxed:[same], audio:[{url,trackId,label,kind,language,bitrate,codec,mime,ranges}],
     *  subtitles:[{url,language,label,mime,auto}],
     *  uploaderAvatar,uploaderUrl,subscribers,uploadedAt(epoch ms, 0 unknown),description,descriptionHtml,
     *  likes,views,chapters:[{title,startSec,preview}],
     *  related:[{id,title,uploader,durationSec,views,date,thumbnail}]}  (the last group is optional)
     */
    String resolveVideo(String videoId) throws EngineException;

    /**
     * How a streaming (videoplayback) request to [url] must look, as JSON:
     * {userAgent: string|null, headers: {name: value}}. YouTube expects different identities for different
     * clients, and that changes, so it lives here rather than in the app. Must never throw.
     */
    String streamProfile(String url);

    /** Playlist JSON: {title, items:[{id,title,uploader,durationSec}], truncated}. */
    String loadPlaylist(String playlistUrl, int maxItems) throws EngineException;

    /**
     * Top-level comments, as JSON: {disabled, count, items:[{author,avatar,text,likes,date,pinned}]}.
     * [max] limits the number of items returned.
     */
    String comments(String videoId, int max) throws EngineException;
}
