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
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Pierantonio Cangianiello
 */
public class SelfExpiringHashMapTests {

    private final static int SLEEP_MULTIPLIER = 80;

    @Test
    public void basicGetTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(1 * SLEEP_MULTIPLIER);
        assertEquals("b", map.get("a"));
    }

    @Test
    public void basicExpireTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(3 * SLEEP_MULTIPLIER);
        assertNull(map.get("a"));
    }

    @Test
    public void basicRenewTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 3 * SLEEP_MULTIPLIER);
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        map.renewKey("a");
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        assertEquals("b", map.get("a"));
    }

    @Test
    public void getRenewTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 3 * SLEEP_MULTIPLIER);
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        assertEquals("b", map.get("a"));
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        assertEquals("b", map.get("a"));
    }

    @Test
    public void multiplePutThenRemoveTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(1 * SLEEP_MULTIPLIER);
        map.put("a", "c", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(1 * SLEEP_MULTIPLIER);
        map.put("a", "d", 400 * SLEEP_MULTIPLIER);
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        assertEquals("d", map.remove("a"));
    }

    @Test
    public void multiplePutThenGetTest() throws InterruptedException {
        SelfExpiringMap<String, String> map = new SelfExpiringHashMap<String, String>();
        map.put("a", "b", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(1 * SLEEP_MULTIPLIER);
        map.put("a", "c", 2 * SLEEP_MULTIPLIER);
        Thread.sleep(1 * SLEEP_MULTIPLIER);
        map.put("a", "d", 400 * SLEEP_MULTIPLIER);
        Thread.sleep(2 * SLEEP_MULTIPLIER);
        assertEquals("d", map.get("a"));
    }

    @Test
    public void insertionOrderTest() throws InterruptedException {
        SelfExpiringMap<String, Integer> map = new SelfExpiringHashMap<String, Integer>(30000);
        map.put("123456", 999);
        assertEquals(map.get("123456"), Integer.valueOf(999));
        map.put("123456", 123);
        map.put("777456", 333);
        assertEquals(map.get("123456"), Integer.valueOf(123));
        assertEquals(map.get("777456"), Integer.valueOf(333));
        map.put("777456", 123);
        map.put("123456", 321);
        assertEquals(map.get("123456"), Integer.valueOf(321));
        assertEquals(map.get("777456"), Integer.valueOf(123));
    }

}