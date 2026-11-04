package com.drishyopot.engine.api;

/** Proof-of-origin tokens, produced by the app (BotGuard needs a real WebView). */
public interface PoTokenBridge {
    /**
     * Blocking. Returns {visitorData, playerRequestPoToken, streamingDataPoToken} for the web client,
     * or null when no token could be produced (the engine then continues without one).
     */
    String[] webClientPoToken(String videoId);
}
