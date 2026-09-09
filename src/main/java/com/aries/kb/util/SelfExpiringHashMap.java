package com.aries.kb.util;

/*
 * Copyright (c) 2019 Pierantonio Cangianiello
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A HashMap which entries expires after the specified life time.
 * The life-time can be defined on a per-key basis, or using a default one, that is passed to the
 * constructor.
 *
 * @author Pierantonio Cangianiello
 * @param <K> the Key type
 * @param <V> the Value type
 */
public class SelfExpiringHashMap<K, V> implements SelfExpiringMap<K, V> {

    private final Map<K, V> internalMap;

    private final Map<K, ExpiringKey<K>> expiringKeys;

    /**
     * Holds the map keys using the given life time for expiration.
     */
    private final DelayQueue<ExpiringKey<K>> delayQueue = new DelayQueue<>();

    /**
     * The default max life time in milliseconds.
     */
    private final long maxLifeTimeMillis;

    private final LongSupplier ticker;

    public SelfExpiringHashMap() {
        this(Long.MAX_VALUE);
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis) {
        this(defaultMaxLifeTimeMillis, 16);
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis, int initialCapacity) {
        this(defaultMaxLifeTimeMillis, initialCapacity, 0.75f);
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis, int initialCapacity, float loadFactor) {
        this(defaultMaxLifeTimeMillis, initialCapacity, loadFactor, System::nanoTime);
    }

    SelfExpiringHashMap(long defaultMaxLifeTimeMillis, LongSupplier ticker) {
        this(defaultMaxLifeTimeMillis, 16, 0.75f, ticker);
    }

    private SelfExpiringHashMap(long defaultMaxLifeTimeMillis, int initialCapacity,
                                float loadFactor, LongSupplier ticker) {
        internalMap = new ConcurrentHashMap<>(initialCapacity, loadFactor);
        expiringKeys = new HashMap<>(initialCapacity, loadFactor);
        this.maxLifeTimeMillis = defaultMaxLifeTimeMillis;
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int size() {
        cleanup();
        return internalMap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isEmpty() {
        cleanup();
        return internalMap.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean containsKey(Object key) {
        cleanup();
        return internalMap.containsKey((K) key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean containsValue(Object value) {
        cleanup();
        return internalMap.containsValue((V) value);
    }

    @Override
    public synchronized V get(Object key) {
        cleanup();
        return internalMap.get((K) key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized V put(K key, V value) {
        return this.put(key, value, maxLifeTimeMillis);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized V put(K key, V value, long lifeTimeMillis) {
        cleanup();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        ExpiringKey<K> delayedKey = new ExpiringKey<>(key, lifeTimeMillis);
        ExpiringKey<K> oldKey = expiringKeys.put(key, delayedKey);
        if (oldKey != null) {
            delayQueue.remove(oldKey);
        }
        delayQueue.offer(delayedKey);
        return internalMap.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized V remove(Object key) {
        V removedValue = internalMap.remove((K) key);
        ExpiringKey<K> delayedKey = expiringKeys.remove(key);
        if (delayedKey != null) {
            delayQueue.remove(delayedKey);
        }
        return removedValue;
    }

    /**
     * Not supported.
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean renewKey(K key) {
        cleanup();
        ExpiringKey<K> delayedKey = expiringKeys.get(key);
        if (delayedKey != null) {
            delayQueue.remove(delayedKey);
            delayedKey.renew();
            delayQueue.offer(delayedKey);
            return true;
        }
        return false;
    }

    /** Remaining lifetime for diagnostics; reading does not extend it. */
    public synchronized long remainingMillis(Object key) {
        cleanup();
        ExpiringKey<K> delayedKey = expiringKeys.get(key);
        return delayedKey == null ? -1L : Math.max(0L, delayedKey.getDelay(TimeUnit.MILLISECONDS));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void clear() {
        delayQueue.clear();
        expiringKeys.clear();
        internalMap.clear();
    }

    /**
     * Not supported.
     */
    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    private void cleanup() {
        ExpiringKey<K> delayedKey = delayQueue.poll();
        while (delayedKey != null) {
            if (expiringKeys.get(delayedKey.getKey()) == delayedKey) {
                internalMap.remove(delayedKey.getKey());
                expiringKeys.remove(delayedKey.getKey());
            }
            delayedKey = delayQueue.poll();
        }
    }

    private class ExpiringKey<K> implements Delayed {

        private long startNanos = ticker.getAsLong();
        private final long maxLifeTimeMillis;
        private final K key;

        public ExpiringKey(K key, long maxLifeTimeMillis) {
            this.maxLifeTimeMillis = maxLifeTimeMillis;
            this.key = key;
        }

        public K getKey() {
            return key;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ExpiringKey<K> other = (ExpiringKey<K>) obj;
            if (this.key != other.key && (this.key == null || !this.key.equals(other.key))) {
                return false;
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + (this.key != null ? this.key.hashCode() : 0);
            return hash;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(remainingNanos(ticker.getAsLong()), TimeUnit.NANOSECONDS);
        }

        private long remainingNanos(long now) {
            return TimeUnit.MILLISECONDS.toNanos(maxLifeTimeMillis) - (now - startNanos);
        }

        public void renew() {
            startNanos = ticker.getAsLong();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(Delayed that) {
            long now = ticker.getAsLong();
            return Long.compare(this.remainingNanos(now), ((ExpiringKey) that).remainingNanos(now));
        }
    }
}
