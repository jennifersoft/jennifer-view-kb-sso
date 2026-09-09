package com.aries.kb.auth;

import org.junit.Test;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class AuthKeyGeneratorTests {
    @Test
    public void repeatedTimeAndRandomnessStillProduceThreeDistinctKeys() {
        AuthKeyGenerator generator = generator(1000L, 0, 0);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 3; i++) assertTrue(keys.add(generator.issue("user", "device")));
    }

    @Test
    public void usesUserDeviceTimeSecretAndNonceInTheKey() {
        String reference = generator(1000L, 0, 0).issue("user", "device");
        assertEquals(reference, generator(1000L, 0, 0).issue("user", "device"));
        assertNotEquals(reference, generator(1000L, 0, 0).issue("other", "device"));
        assertNotEquals(reference, generator(1000L, 0, 0).issue("user", "other"));
        assertNotEquals(reference, generator(1001L, 0, 0).issue("user", "device"));
        assertNotEquals(reference, generator(11000L, 0, 0).issue("user", "device"));
        assertNotEquals(reference, generator(1000L, 1, 0).issue("user", "device"));
        assertNotEquals(reference, generator(1000L, 0, 1).issue("user", "device"));
    }

    @Test
    public void preservesBothIdentityBoundariesInKeyAndCacheIndex() {
        assertNotEquals(generator(1000L, 0, 0).issue("ab", "c"), generator(1000L, 0, 0).issue("a", "bc"));
        assertNotEquals(AuthKeyGenerator.identityKey("ab", "c"), AuthKeyGenerator.identityKey("a", "bc"));
    }

    @Test
    public void generatedKeysNeedNoEscapingOfPlusSlashOrPadding() {
        for (int i = 0; i < 1000; i++) {
            String key = AuthKeyGenerator.generate("user", "device");
            assertTrue(key.matches("[A-Za-z0-9_-]{43}"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyIdentity() { AuthKeyGenerator.generate("", "device"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedIdentity() { AuthKeyGenerator.generate("u".repeat(257), "device"); }

    @Test(timeout = 5000L)
    public void concurrentIssuanceDoesNotRepeatASequence() throws Exception {
        AuthKeyGenerator generator = generator(1000L, 0, 0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Set<String> keys = ConcurrentHashMap.newKeySet();
            java.util.List<Future<?>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> {
                for (int j = 0; j < 100; j++) assertTrue(keys.add(generator.issue("user", "device")));
            }));
            for (Future<?> task : tasks) task.get(3L, TimeUnit.SECONDS);
            assertEquals(800, keys.size());
        } finally { executor.shutdownNow(); }
    }

    private AuthKeyGenerator generator(long millis, int secret, int nonce) {
        return new AuthKeyGenerator(Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC), new SecureRandom() {
            private boolean initialized;
            @Override public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) (initialized ? nonce : secret));
                initialized = true;
            }
        });
    }
}
