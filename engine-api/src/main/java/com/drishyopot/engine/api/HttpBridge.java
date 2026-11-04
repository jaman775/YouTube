package com.drishyopot.engine.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** HTTP access provided by the app (so cookies, proxies and TLS settings stay in one place). */
public interface HttpBridge {
    HttpResult execute(String method, String url, Map<String, List<String>> headers, byte[] body,
                       String languageTag) throws IOException;

    /** Result of a request. */
    final class HttpResult {
        public final int code;
        public final String message;
        public final Map<String, List<String>> headers;
        public final String body;
        public final String finalUrl;

        public HttpResult(int code, String message, Map<String, List<String>> headers, String body, String finalUrl) {
            this.code = code;
            this.message = message;
            this.headers = headers;
            this.body = body;
            this.finalUrl = finalUrl;
        }
    }
}
