package com.drishyopot.engine.impl;

import com.drishyopot.engine.api.PoTokenBridge;

import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;

import javax.annotation.Nullable;

/** Asks the app for PO tokens. Any failure means "no token": the extractor then works without one. */
final class BridgePoTokenProvider implements PoTokenProvider {
    private final PoTokenBridge bridge;

    BridgePoTokenProvider(PoTokenBridge bridge) {
        this.bridge = bridge;
    }

    @Nullable
    @Override
    public PoTokenResult getWebClientPoToken(String videoId) {
        try {
            String[] t = bridge.webClientPoToken(videoId);
            if (t == null || t.length < 3 || t[0] == null || t[0].isEmpty() || t[1] == null || t[1].isEmpty()) return null;
            return new PoTokenResult(t[0], t[1], t[2] == null || t[2].isEmpty() ? null : t[2]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The iOS client needs no token from us; "no token" lets the extractor carry on without one. */
    @Nullable
    @Override
    public PoTokenResult getIosClientPoToken(String videoId) {
        return null;
    }

    /** The app only produces web PO tokens. "No token" is the extractor's documented way to carry on without one. */
    @Nullable
    @Override
    public PoTokenResult getAndroidClientPoToken(String videoId) {
        return null;
    }

    /** Not every extractor version declares this one, so there is deliberately no @Override. */
    @Nullable
    public PoTokenResult getWebEmbedClientPoToken(String videoId) {
        return null;
    }
}
