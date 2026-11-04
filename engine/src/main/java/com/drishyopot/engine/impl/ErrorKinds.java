package com.drishyopot.engine.impl;

import com.drishyopot.engine.api.EngineException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps extractor failures and URLs to the stable categories the app understands. */
final class ErrorKinds {
    private ErrorKinds() { }

    private static final Pattern WATCH_ID = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})");
    private static final Pattern PATH_ID = Pattern.compile("(?:youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})");

    /** Extracts the video ID from any YouTube video URL, or null. */
    static String videoId(String url) {
        if (url == null) return null;
        Matcher m = WATCH_ID.matcher(url);
        if (m.find()) return m.group(1);
        m = PATH_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** Decides the category from the exception's class name and message, without depending on its class. */
    static EngineException wrap(Throwable t) {
        String type = t.getClass().getSimpleName();
        String message = String.valueOf(t.getMessage());
        String lower = message.toLowerCase();
        String kind;
        if (type.contains("AgeRestricted")) kind = EngineException.AGE_RESTRICTED;
        else if (type.contains("SignInConfirmNotBot") || lower.contains("not a bot")) kind = EngineException.BOT_CHECK;
        else if (lower.contains("premiere")) kind = EngineException.PREMIERE;
        else if (type.contains("ReCaptcha") || lower.contains("429")) kind = EngineException.RATE_LIMITED;
        else if (type.contains("PrivateContent") || type.contains("PaidContent") || lower.contains("sign in")) kind = EngineException.SIGN_IN_REQUIRED;
        else if (type.contains("ContentNotAvailable") || type.contains("GeographicRestriction")) kind = EngineException.UNAVAILABLE;
        else if (t instanceof IOException) kind = EngineException.NETWORK;
        else kind = EngineException.OTHER;
        return new EngineException(kind, message, t);
    }
}
