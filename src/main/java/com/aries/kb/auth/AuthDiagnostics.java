package com.aries.kb.auth;

import com.aries.extension.util.LogUtil;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Diagnostics identify keys by a one-way tag, never by their original value. */
public final class AuthDiagnostics {
    public static final String VERSION = "3.0.0";
    public static final String BUILD = "v2-memory-1";

    private AuthDiagnostics() { }

    public static String tag(String value) {
        if (value == null) return "missing";
        if (value.length() > 4096) return "oversized";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String details(String userId, String deviceId, String key, Object store) {
        return "version=" + VERSION + " build=" + BUILD
            + " process=" + safe(ManagementFactory.getRuntimeMXBean().getName())
            + " store=" + Integer.toHexString(System.identityHashCode(store))
            + " user_tag=" + tag(userId) + " device_tag=" + tag(deviceId)
            + " key_tag=" + tag(key) + " key_length=" + (key == null ? 0 : key.length());
    }

    public static void runtime(String role, Class<?> owner, Object store) {
        String source = "unavailable";
        try {
            if (owner.getProtectionDomain().getCodeSource() != null) {
                source = owner.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            }
        } catch (SecurityException ignored) {
            // Logging must not prevent authentication when code-source access is restricted.
        }
        LogUtil.info("AUTH_RUNTIME role=" + role + " " + details(null, null, null, store)
            + " loader=" + safe(String.valueOf(owner.getClassLoader())) + " jar=" + safe(source));
    }

    private static String safe(String value) {
        return value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
    }
}
