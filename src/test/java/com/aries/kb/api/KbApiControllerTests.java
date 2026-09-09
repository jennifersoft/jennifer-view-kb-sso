package com.aries.kb.api;

import org.junit.Test;
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
    public void returnsDifferentTokensForRepeatedRequests() {
        KbApiController controller = new KbApiController();

        String first = controller.createAuthKey("user-repeat", "device-1").getBody();
        String second = controller.createAuthKey("user-repeat", "device-1").getBody();

        assertNotEquals(first, second);
    }

    @Test
    public void rejectsAnOversizedIdentity() {
        KbApiController controller = new KbApiController();
        String oversized = new String(new char[257]).replace('\0', 'u');

        ResponseEntity<String> response = controller.createAuthKey(oversized, "device-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
