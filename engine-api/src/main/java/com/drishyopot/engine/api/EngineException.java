package com.drishyopot.engine.api;

/** Failure inside the engine. [kind] is a stable machine-readable category. */
public class EngineException extends Exception {
    public static final String LIVE = "LIVE";
    public static final String PREMIERE = "PREMIERE";
    public static final String AGE_RESTRICTED = "AGE_RESTRICTED";
    public static final String SIGN_IN_REQUIRED = "SIGN_IN_REQUIRED";
    public static final String BOT_CHECK = "BOT_CHECK";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String NETWORK = "NETWORK";
    public static final String OTHER = "OTHER";

    public final String kind;

    public EngineException(String kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public EngineException(String kind, String message) {
        this(kind, message, null);
    }
}
