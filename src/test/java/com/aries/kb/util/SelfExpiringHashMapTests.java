package com.aries.kb.util;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class SelfExpiringHashMapTests {
    private final AtomicLong nanos = new AtomicLong();
    private final SelfExpiringHashMap<String, String> map = new SelfExpiringHashMap<>(10000L, nanos::get);

    private void advance(long millis) { nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis)); }

    @Test
    public void acceptsAt9999MillisAndExpiresExactlyAt10000() {
        map.put("user", "key");
        advance(9999L);
        assertEquals("key", map.get("user"));
        assertEquals(1L, map.remainingMillis("user"));
        advance(1L);
        assertNull(map.get("user"));
        assertEquals(-1L, map.remainingMillis("user"));
    }

    @Test
    public void repeatedLookupsDoNotExtendTheIssuanceDeadline() {
        map.put("user", "key");
        for (int i = 0; i < 9; i++) { advance(1000L); assertEquals("key", map.get("user")); }
        advance(1000L);
        assertNull(map.get("user"));
    }

    @Test
    public void replacementHasItsOwnDeadlineAndSurvivesTheOldDeadline() {
        map.put("user", "first");
        map.put("other", "other");
        advance(9000L);
        assertEquals("first", map.put("user", "second"));
        advance(1000L);
        assertNull(map.get("other"));
        assertEquals("second", map.get("user"));
        advance(9000L);
        assertNull(map.get("user"));
    }

    @Test
    public void explicitRenewalReordersTheExpirationQueue() {
        map.put("first", "a", 100L);
        map.put("second", "b", 150L);
        advance(90L);
        assertTrue(map.renewKey("first"));
        advance(60L);
        assertNull(map.get("second"));
        assertEquals("a", map.get("first"));
        advance(40L);
        assertNull(map.get("first"));
    }

    @Test
    public void expiredKeysCannotBeRenewed() {
        map.put("user", "key"); advance(10000L);
        assertFalse(map.renewKey("user"));
    }

    @Test
    public void removalAndClearRemoveExpirationRecordsToo() {
        map.put("user", "first");
        assertEquals("first", map.remove("user"));
        map.put("user", "second");
        map.clear();
        assertTrue(map.isEmpty());
        map.put("user", "third");
        assertEquals("third", map.get("user"));
    }

    @Test
    public void allReadOperationsDiscardExpiredEntries() {
        map.put("user", "key"); advance(10000L);
        assertFalse(map.containsKey("user"));
        assertFalse(map.containsValue("key"));
        assertEquals(0, map.size());
    }

    @Test(timeout = 5000L)
    public void concurrentReplacementAndReadingDoNotLoseAnUnexpiredKey() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> {
                for (int j = 0; j < 100; j++) { map.put("user", Integer.toString(j)); assertNotNull(map.get("user")); }
            }));
            for (Future<?> task : tasks) task.get(3L, TimeUnit.SECONDS);
            map.put("user", "final");
            assertEquals("final", map.get("user"));
            advance(10000L);
            assertNull(map.get("user"));
        } finally { executor.shutdownNow(); }
    }
}
