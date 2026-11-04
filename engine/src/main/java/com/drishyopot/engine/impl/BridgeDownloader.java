package com.drishyopot.engine.impl;

import com.drishyopot.engine.api.HttpBridge;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;

/** Sends the extractor's requests through the app's HTTP layer. */
final class BridgeDownloader extends Downloader {
    private final HttpBridge http;

    BridgeDownloader(HttpBridge http) {
        this.http = http;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpBridge.HttpResult r = http.execute(
                request.httpMethod(), request.url(), request.headers(), request.dataToSend(), null);
        if (r.code == 429) throw new ReCaptchaException("Rate limited (429)", request.url());
        return new Response(r.code, r.message, r.headers, r.body, r.finalUrl);
    }
}
