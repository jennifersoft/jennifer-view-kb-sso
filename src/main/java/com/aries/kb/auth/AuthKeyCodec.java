package com.aries.kb.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class AuthKeyCodec {
    private AuthKeyCodec() { }

    /**
     * Servlet query parsing already decoded once. A v2 API response may have been
     * encoded again by the GET caller. Decode that remaining percent layer once,
     * preserving literal '+' characters in an already decoded Base64 key.
     */
    public static String normalize(String key) {
        if (key == null || key.isEmpty() || key.length() > 4096) {
            throw new IllegalArgumentException("invalid authentication key length");
        }
        return key.indexOf('%') < 0 ? key
            : URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
