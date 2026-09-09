package com.aries.kb.api;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class KbApiControllerTests {

    @Test
    public void returnsA256BitUrlSafeAuthenticationToken() {
        KbApiController controller = new KbApiController();

        ResponseEntity<String> response = controller.createAuthKey("user-1", "device-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    public void returnsTheSameKeyForRepeatedRequestsWithinTenSeconds() {
        KbApiController controller = new KbApiController();

        String first = controller.createAuthKey("user-repeat", "device-1").getBody();
        String second = controller.createAuthKey("user-repeat", "device-1").getBody();

        assertEquals(first, second);
    }

    @Test
    public void preventsAuthenticationTokenResponsesFromBeingCached() {
        ResponseEntity<String> response = new KbApiController().createAuthKey("user-cache", "device-1");

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst(HttpHeaders.PRAGMA));
    }

    @Test
    public void identifiesTheLoadedVersionAndEachIssuanceWithoutExposingTheKeyInHeaders() {
        KbApiController controller = new KbApiController();
        ResponseEntity<String> first = controller.createAuthKey("user-diagnostics", "device-1");
        ResponseEntity<String> second = controller.createAuthKey("user-diagnostics", "device-1");

        assertEquals("3.0.0", first.getHeaders().getFirst("X-KB-SSO-Version"));
        String firstId = first.getHeaders().getFirst("X-KB-SSO-Issuance-Id");
        String secondId = second.getHeaders().getFirst("X-KB-SSO-Issuance-Id");
        assertNotNull(firstId);
        assertNotNull(secondId);
        assertNotEquals(firstId, secondId);
        assertNotEquals(first.getBody(), firstId);
        assertNotEquals(second.getBody(), secondId);
    }

    @Test
    public void rejectsAnOversizedIdentity() {
        KbApiController controller = new KbApiController();
        String oversized = new String(new char[257]).replace('\0', 'u');

        ResponseEntity<String> response = controller.createAuthKey(oversized, "device-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
