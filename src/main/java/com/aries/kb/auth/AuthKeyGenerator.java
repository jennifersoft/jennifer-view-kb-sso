package com.aries.kb.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Generates opaque keys only. Issued keys remain in KbLoginAdapter.AUTH_KEYS, as in v2. */
public final class AuthKeyGenerator {
    private static final AuthKeyGenerator DEFAULT = new AuthKeyGenerator(Clock.systemUTC(), new SecureRandom());
    private final Clock clock;
    private final SecureRandom random;
    private final SecretKeySpec signingKey;
    private final AtomicLong sequence = new AtomicLong();

    AuthKeyGenerator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        signingKey = new SecretKeySpec(secret, "HmacSHA256");
    }

    public static String generate(String userId, String deviceId) {
        return DEFAULT.issue(userId, deviceId);
    }

    public static boolean validIdentity(String value) {
        return value != null && !value.isEmpty() && value.length() <= 256;
    }

    public static String identityKey(String userId, String deviceId) {
        if (!validIdentity(userId) || !validIdentity(deviceId)) {
            throw new IllegalArgumentException("userId and deviceId must contain 1 to 256 characters");
        }
        // Keep the v2 string-keyed cache, with unambiguous user/device boundaries.
        return userId.length() + ":" + userId + deviceId;
    }

    String issue(String userId, String deviceId) {
        identityKey(userId, deviceId);
        byte[] user = userId.getBytes(StandardCharsets.UTF_8);
        byte[] device = deviceId.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        byte[] payload = ByteBuffer.allocate(2 * Integer.BYTES + user.length + device.length
                + 2 * Long.BYTES + nonce.length)
            .putInt(user.length).put(user).putInt(device.length).put(device)
            .putLong(clock.millis()).putLong(sequence.getAndIncrement()).put(nonce).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
