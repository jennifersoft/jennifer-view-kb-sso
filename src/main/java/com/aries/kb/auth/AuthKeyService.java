package com.aries.kb.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class AuthKeyService {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final int MAX_ID_LENGTH = 256;
    private static final int DEFAULT_MAX_ACTIVE_TOKENS = 100_000;
    private static final long DEFAULT_TTL_MILLIS = 10_000L;
    private static final AuthKeyService SHARED = new AuthKeyService(DEFAULT_TTL_MILLIS);

    private final long ttlNanos;
    private final LongSupplier ticker;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final SecretKeySpec signingKey;
    private final int maxActiveTokens;
    private final Object lock = new Object();
    private final Map<ClientIdentity, TokenRecord> tokensByIdentity = new HashMap<>();
    private final NavigableSet<TokenRecord> recordsByExpiry = new TreeSet<>();
    private long recordSequence;

    public AuthKeyService(long ttlMillis) {
        this(ttlMillis, System::nanoTime, new SecureRandom(), DEFAULT_MAX_ACTIVE_TOKENS);
    }

    public static AuthKeyService shared() {
        return SHARED;
    }

    AuthKeyService(long ttlMillis, LongSupplier ticker, SecureRandom secureRandom) {
        this(ttlMillis, ticker, secureRandom, DEFAULT_MAX_ACTIVE_TOKENS);
    }

    AuthKeyService(long ttlMillis, LongSupplier ticker, SecureRandom secureRandom, int maxActiveTokens) {
        this(ttlMillis, ticker, secureRandom, maxActiveTokens, Clock.systemUTC());
    }

    AuthKeyService(long ttlMillis, LongSupplier ticker, SecureRandom secureRandom,
                   int maxActiveTokens, Clock clock) {
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("ttlMillis must be greater than zero");
        }
        if (maxActiveTokens <= 0) {
            throw new IllegalArgumentException("maxActiveTokens must be greater than zero");
        }
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMillis);
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.maxActiveTokens = maxActiveTokens;
        byte[] secret = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(secret);
        this.signingKey = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String issue(String userId, String deviceId) {
        validateIdentity(userId, deviceId);
        ClientIdentity identity = new ClientIdentity(userId, deviceId);

        byte[] nonce = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(nonce);

        synchronized (lock) {
            long now = ticker.getAsLong();
            evictExpired(now);
            TokenRecord previous = tokensByIdentity.get(identity);
            if (previous == null && tokensByIdentity.size() >= maxActiveTokens) {
                throw new IllegalStateException("active authentication token capacity exceeded");
            }

            // Sequence remains unique even across consumption, clock rollback, or repeated nonces.
            long sequence = recordSequence++;
            String token = generateToken(userId, deviceId, clock.millis(), sequence, nonce);
            TokenRecord replacement = new TokenRecord(identity, hash(token), now + ttlNanos, sequence);
            if (previous != null) {
                recordsByExpiry.remove(previous);
            }
            tokensByIdentity.put(identity, replacement);
            recordsByExpiry.add(replacement);
            return token;
        }
    }

    private String generateToken(String userId, String deviceId, long issuedAtMillis,
                                 long sequence, byte[] nonce) {
        byte[] userBytes = userId.getBytes(StandardCharsets.UTF_8);
        byte[] deviceBytes = deviceId.getBytes(StandardCharsets.UTF_8);
        // Length prefixes distinguish identities such as ("ab", "c") and ("a", "bc").
        byte[] payload = ByteBuffer.allocate(2 * Integer.BYTES + userBytes.length + deviceBytes.length
                + 2 * Long.BYTES + nonce.length)
            .putInt(userBytes.length).put(userBytes)
            .putInt(deviceBytes.length).put(deviceBytes)
            .putLong(issuedAtMillis)
            .putLong(sequence)
            .put(nonce)
            .array();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    public boolean consume(String userId, String deviceId, String token) {
        if (!isValidIdentityPart(userId) || !isValidIdentityPart(deviceId) || !isCanonicalToken(token)) {
            return false;
        }
        byte[] candidateHash = hash(token);
        ClientIdentity identity = new ClientIdentity(userId, deviceId);

        synchronized (lock) {
            evictExpired(ticker.getAsLong());
            TokenRecord record = tokensByIdentity.get(identity);
            if (record == null || !MessageDigest.isEqual(record.tokenHash, candidateHash)) {
                return false;
            }
            tokensByIdentity.remove(identity);
            recordsByExpiry.remove(record);
            return true;
        }
    }

    private void validateIdentity(String userId, String deviceId) {
        if (!isValidIdentityPart(userId) || !isValidIdentityPart(deviceId)) {
            throw new IllegalArgumentException("userId and deviceId must contain 1 to 256 characters");
        }
    }

    private boolean isValidIdentityPart(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_ID_LENGTH;
    }

    private boolean isCanonicalToken(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            boolean valid = character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '-'
                || character == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private void evictExpired(long now) {
        while (!recordsByExpiry.isEmpty()) {
            TokenRecord record = recordsByExpiry.first();
            if (now < record.expiresAtNanos) {
                return;
            }
            recordsByExpiry.pollFirst();
            tokensByIdentity.remove(record.identity, record);
        }
    }

    private byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static final class TokenRecord implements Comparable<TokenRecord> {
        private final ClientIdentity identity;
        private final byte[] tokenHash;
        private final long expiresAtNanos;
        private final long sequence;

        private TokenRecord(ClientIdentity identity, byte[] tokenHash, long expiresAtNanos, long sequence) {
            this.identity = identity;
            this.tokenHash = tokenHash;
            this.expiresAtNanos = expiresAtNanos;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(TokenRecord other) {
            int byExpiry = Long.compare(expiresAtNanos, other.expiresAtNanos);
            return byExpiry != 0 ? byExpiry : Long.compare(sequence, other.sequence);
        }
    }

    private static final class ClientIdentity {
        private final String userId;
        private final String deviceId;

        private ClientIdentity(String userId, String deviceId) {
            this.userId = userId;
            this.deviceId = deviceId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ClientIdentity)) {
                return false;
            }
            ClientIdentity that = (ClientIdentity) other;
            return Objects.equals(userId, that.userId) && Objects.equals(deviceId, that.deviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, deviceId);
        }
    }
}
