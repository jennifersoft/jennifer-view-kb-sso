package com.aries.kb.auth;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AuthKeyServiceTests {

    @Test
    public void issuesDifferentTokensForTheSameUserAndDevice() {
        AuthKeyService service = new AuthKeyService(10_000L);

        String first = service.issue("user-1", "device-1");
        String second = service.issue("user-1", "device-1");

        assertNotEquals(first, second);
    }

    @Test
    public void issuesA256BitUrlSafeToken() {
        AuthKeyService service = new AuthKeyService(10_000L);

        String token = service.issue("user-1", "device-1");

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    public void consumesAValidTokenOnlyOnce() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-1", "device-1");

        assertTrue(service.consume("user-1", "device-1", token));
        assertFalse(service.consume("user-1", "device-1", token));
    }

    @Test
    public void invalidatesThePreviousTokenWhenAReplacementIsIssued() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String previous = service.issue("user-1", "device-1");
        String replacement = service.issue("user-1", "device-1");

        assertFalse(service.consume("user-1", "device-1", previous));
        assertTrue(service.consume("user-1", "device-1", replacement));
    }

    @Test
    public void keepsUserAndDeviceIdentityBoundariesDistinct() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("ab", "c");

        assertFalse(service.consume("a", "bc", token));
        assertTrue(service.consume("ab", "c", token));
    }

    @Test
    public void rejectsATokenAtItsAbsoluteExpiry() {
        MutableTicker ticker = new MutableTicker();
        AuthKeyService service = new AuthKeyService(10_000L, ticker, new SecureRandom());
        String token = service.issue("user-1", "device-1");

        ticker.advanceMillis(10_000L);

        assertFalse(service.consume("user-1", "device-1", token));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsANonPositiveTimeToLive() {
        new AuthKeyService(0L);
    }

    @Test
    public void acceptsATokenImmediatelyBeforeItsExpiry() {
        MutableTicker ticker = new MutableTicker();
        AuthKeyService service = new AuthKeyService(10_000L, ticker, new SecureRandom());
        String token = service.issue("user-before-expiry", "device-1");

        ticker.advanceMillis(9_999L);

        assertTrue(service.consume("user-before-expiry", "device-1", token));
    }

    @Test
    public void failedAttemptDoesNotExtendTheAbsoluteExpiry() {
        MutableTicker ticker = new MutableTicker();
        AuthKeyService service = new AuthKeyService(10_000L, ticker, new SecureRandom());
        String token = service.issue("user-absolute-expiry", "device-1");

        ticker.advanceMillis(9_999L);
        assertFalse(service.consume("user-absolute-expiry", "device-1", "wrong-token"));
        ticker.advanceMillis(1L);

        assertFalse(service.consume("user-absolute-expiry", "device-1", token));
    }

    @Test
    public void wrongTokenDoesNotConsumeTheValidToken() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-wrong-token", "device-1");

        assertFalse(service.consume("user-wrong-token", "device-1", "wrong-token"));
        assertTrue(service.consume("user-wrong-token", "device-1", token));
    }

    @Test
    public void wrongUserDoesNotConsumeTheValidToken() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-owner", "device-1");

        assertFalse(service.consume("user-other", "device-1", token));
        assertTrue(service.consume("user-owner", "device-1", token));
    }

    @Test
    public void wrongDeviceDoesNotConsumeTheValidToken() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-device", "device-owner");

        assertFalse(service.consume("user-device", "device-other", token));
        assertTrue(service.consume("user-device", "device-owner", token));
    }

    @Test
    public void issuingForOneIdentityDoesNotInvalidateAnotherIdentity() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String first = service.issue("user-a", "device-a");
        String second = service.issue("user-b", "device-b");

        assertTrue(service.consume("user-a", "device-a", first));
        assertTrue(service.consume("user-b", "device-b", second));
    }

    @Test
    public void evictsExpiredTokensBeforeEnforcingCapacity() {
        MutableTicker ticker = new MutableTicker();
        AuthKeyService service = new AuthKeyService(10_000L, ticker, new SecureRandom(), 2);
        service.issue("user-a", "device-a");
        service.issue("user-b", "device-b");

        ticker.advanceMillis(10_000L);

        String token = service.issue("user-c", "device-c");
        assertTrue(service.consume("user-c", "device-c", token));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNewIdentitiesWhenActiveTokenCapacityIsFull() {
        AuthKeyService service = new AuthKeyService(10_000L, System::nanoTime, new SecureRandom(), 2);
        service.issue("user-a", "device-a");
        service.issue("user-b", "device-b");

        service.issue("user-c", "device-c");
    }

    @Test
    public void rejectsOversizedTokenBeforeLeavingTheValidTokenUntouched() {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-token-limit", "device-1");
        char[] oversized = new char[100_000];
        Arrays.fill(oversized, 'A');

        assertFalse(service.consume("user-token-limit", "device-1", new String(oversized)));
        assertTrue(service.consume("user-token-limit", "device-1", token));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedIdentityValues() {
        AuthKeyService service = new AuthKeyService(10_000L);
        char[] oversized = new char[257];
        Arrays.fill(oversized, 'u');

        service.issue(new String(oversized), "device-1");
    }

    @Test
    public void retriesWhenTheRandomSourceRepeatsThePreviousToken() {
        RepeatingThenChangingSecureRandom random = new RepeatingThenChangingSecureRandom();
        AuthKeyService service = new AuthKeyService(10_000L, System::nanoTime, random);

        String first = service.issue("user-repeat-rng", "device-1");
        String second = service.issue("user-repeat-rng", "device-1");

        assertNotEquals(first, second);
        assertEquals(3, random.calls);
    }

    @Test(timeout = 5_000L)
    public void allowsOnlyOneSuccessfulConcurrentConsumption() throws Exception {
        AuthKeyService service = new AuthKeyService(10_000L);
        String token = service.issue("user-concurrent", "device-1");
        int contenderCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < contenderCount; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.consume("user-concurrent", "device-1", token);
                }));
            }

            assertTrue(ready.await(2L, TimeUnit.SECONDS));
            start.countDown();

            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(2L, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertEquals(1, successCount);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static final class MutableTicker implements LongSupplier {
        private long currentNanos;

        private void advanceMillis(long millis) {
            currentNanos += TimeUnit.MILLISECONDS.toNanos(millis);
        }

        @Override
        public long getAsLong() {
            return currentNanos;
        }
    }

    private static final class RepeatingThenChangingSecureRandom extends SecureRandom {
        private int calls;

        @Override
        public void nextBytes(byte[] bytes) {
            calls++;
            Arrays.fill(bytes, calls <= 2 ? (byte) 0 : (byte) 1);
        }
    }
}
