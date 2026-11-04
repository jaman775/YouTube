package com.drishyopot.engine.impl;

import com.drishyopot.engine.api.EngineException;
import java.io.IOException;
import java.util.*;
import org.junit.Test;

public class EngineHelpersTest {
    static void eq(Object a, Object b) { if (!Objects.equals(a, b)) throw new AssertionError(a + " != " + b); }
    static class AgeRestrictedContentException extends Exception { AgeRestrictedContentException(String m) { super(m); } }
    static class ContentNotAvailableException extends Exception { ContentNotAvailableException(String m) { super(m); } }
    static class SignInConfirmNotBotException extends Exception { SignInConfirmNotBotException(String m) { super(m); } }
    @Test
    public void helpers() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "q\"uote\\ \n\u0001 দৃশ্য");
        m.put("n", 5L); m.put("d", 1.5); m.put("nan", Double.NaN); m.put("b", true); m.put("z", null);
        m.put("r", new long[] {1, 2, 3, 4});
        m.put("l", Arrays.asList("x", 2, Collections.singletonMap("k", "v")));
        eq(JsonOut.write(m), "{\"s\":\"q\\\"uote\\\\ \\n\\u0001 দৃশ্য\",\"n\":5,\"d\":1.5,\"nan\":null,\"b\":true,\"z\":null,\"r\":[1,2,3,4],\"l\":[\"x\",2,{\"k\":\"v\"}]}");

        eq(ErrorKinds.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), "dQw4w9WgXcQ");
        eq(ErrorKinds.videoId("https://www.youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=3"), "dQw4w9WgXcQ");
        eq(ErrorKinds.videoId("https://youtu.be/dQw4w9WgXcQ?t=2"), "dQw4w9WgXcQ");
        eq(ErrorKinds.videoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"), "dQw4w9WgXcQ");
        eq(ErrorKinds.videoId("https://example.com/"), null);
        eq(ErrorKinds.videoId(null), null);

        eq(ErrorKinds.wrap(new AgeRestrictedContentException("age")).kind, EngineException.AGE_RESTRICTED);
        eq(ErrorKinds.wrap(new SignInConfirmNotBotException("Sign in to confirm you're not a bot")).kind, EngineException.BOT_CHECK);
        eq(ErrorKinds.wrap(new ContentNotAvailableException("This video is a premiere")).kind, EngineException.PREMIERE);
        eq(ErrorKinds.wrap(new ContentNotAvailableException("gone")).kind, EngineException.UNAVAILABLE);
        eq(ErrorKinds.wrap(new IOException("timeout")).kind, EngineException.NETWORK);
        eq(ErrorKinds.wrap(new IllegalStateException("x")).kind, EngineException.OTHER);
    }
}
