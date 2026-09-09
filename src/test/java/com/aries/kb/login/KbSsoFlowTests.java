package com.aries.kb.login;

import com.aries.extension.data.UserData;
import com.aries.kb.api.KbApiController;
import com.aries.kb.auth.AuthKeyGenerator;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class KbSsoFlowTests {
    private MockMvc http;
    private JenniferLoginBoundary login;
    private static final String USER = "bank-user";
    private static final String DEVICE = "bank-device";

    @Before
    public void setup() {
        KbLoginAdapter.Companion.getAUTH_KEYS().clear();
        login = new JenniferLoginBoundary();
        http = MockMvcBuilders.standaloneSetup(new KbApiController(), login).build();
    }

    @Test
    public void getApiResponseIsPassedThroughTheLoginUrlToTheRealPreHandle() throws Exception {
        String key = issue(USER, DEVICE);
        assertEquals(key, KbLoginAdapter.Companion.getAUTH_KEYS().get(AuthKeyGenerator.identityKey(USER, DEVICE)));
        http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isOk()).andExpect(content().string("guest"));
        assertEquals(1, login.calls);
        assertEquals(key, login.receivedKey);
        assertEquals(USER, login.receivedUserId);
        assertEquals(DEVICE, login.receivedDeviceId);
        assertEquals("guest", login.user.id);
        assertEquals("guest", login.user.password);
    }

    @Test
    public void threeLoginsAfterLogoutReuseTheTenSecondKeyAndAllowRepeatedPreHandleChecks() throws Exception {
        String firstKey = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            String key = issue(USER, DEVICE);
            if (firstKey == null) firstKey = key;
            assertEquals(firstKey, key);
            MvcResult result = http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isOk()).andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            http.perform(get(loginUrl(USER, DEVICE, encode(key))).session(session)).andExpect(status().isOk());
            http.perform(get("/test/logout").session(session)).andExpect(status().isNoContent());
        }
    }

    @Test
    public void getDecodesUnicodeSpacesPlusSlashEqualsAndPercentInIdentityExactlyOnce() throws Exception {
        String user = "은행+사용자 /=%";
        String device = "단말 +/=%";
        String key = issue(user, device);
        http.perform(get(loginUrl(user, device, encode(key)))).andExpect(status().isOk()).andExpect(content().string("guest"));
        assertEquals(user, login.receivedUserId);
        assertEquals(device, login.receivedDeviceId);
        assertEquals("guest", login.user.id);
    }

    @Test
    public void percentEncodedAndTwiceEncodedKeysBothReachPreHandleCorrectly() throws Exception {
        String key = issue(USER, DEVICE);
        StringBuilder escaped = new StringBuilder();
        for (byte value : key.getBytes(StandardCharsets.UTF_8)) escaped.append(String.format("%%%02X", value & 255));
        http.perform(get(loginUrl(USER, DEVICE, escaped.toString()))).andExpect(status().isOk());
        assertEquals(key, login.receivedKey);
        http.perform(get(loginUrl(USER, DEVICE, encode(escaped.toString())))).andExpect(status().isOk());
        assertEquals(escaped.toString(), login.receivedKey);
    }

    @Test
    public void legacyEncodedCacheWithPlusSlashAndPaddingSupportsGetCallersEncodingAgain() throws Exception {
        byte[] bytes = new byte[16];
        Arrays.fill(bytes, (byte) 0xff);
        bytes[0] = (byte) 0xfb;
        String raw = Base64.getEncoder().encodeToString(bytes);
        assertTrue(raw.contains("+") && raw.contains("/") && raw.contains("="));
        String apiResponse = encode(raw);
        KbLoginAdapter.Companion.getAUTH_KEYS().put(AuthKeyGenerator.identityKey(USER, DEVICE), apiResponse, 10000L);
        // v2 caller appends the already encoded API response directly to its GET URL.
        http.perform(get(loginUrl(USER, DEVICE, apiResponse))).andExpect(status().isOk());
        assertEquals(raw, login.receivedKey);
        // URI-building clients encode the API response again: %2F becomes %252F on the wire.
        http.perform(get(loginUrl(USER, DEVICE, encode(apiResponse)))).andExpect(status().isOk());
        assertEquals(apiResponse, login.receivedKey);
    }

    @Test
    public void malformedPercentEncodingIsRejectedWithoutLosingTheValidKey() throws Exception {
        String key = issue(USER, DEVICE);
        http.perform(get(loginUrl(USER, DEVICE, encode("%ZZ")))).andExpect(status().isUnauthorized());
        http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isOk());
    }

    @Test
    public void mismatchedIdentitiesCannotAuthenticateWithAnotherUsersKey() throws Exception {
        String key = issue("ab", "c");
        http.perform(get(loginUrl("a", "bc", encode(key)))).andExpect(status().isUnauthorized());
        http.perform(get(loginUrl("ab", "c", encode(key)))).andExpect(status().isOk());
    }

    @Test(timeout = 15000L)
    public void realApiKeyAuthenticatesBeforeTenSecondsExpiresAndCanBeReissued() throws Exception {
        String key = issue(USER, DEVICE);
        long responseAt = System.nanoTime();
        Thread.sleep(500L);
        assertEquals("An API cache hit must neither replace the key nor renew its expiry", key, issue(USER, DEVICE));
        http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isOk());
        long remaining = 10100L - (System.nanoTime() - responseAt) / 1000000L;
        if (remaining > 0L) Thread.sleep(remaining);
        http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isUnauthorized());
        String replacement = issue(USER, DEVICE);
        assertNotEquals(key, replacement);
        http.perform(get(loginUrl(USER, DEVICE, encode(replacement)))).andExpect(status().isOk());
    }

    @Test
    public void logsCorrelateIssuanceAndValidationWithoutWritingKeysOrCredentials() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        String key;
        try (PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            key = issue(USER, DEVICE);
            http.perform(get(loginUrl(USER, DEVICE, encode(key)))).andExpect(status().isOk());
            http.perform(get(loginUrl(USER, DEVICE, "wrong-key"))).andExpect(status().isUnauthorized());
        } finally { System.setOut(original); }
        String logs = buffer.toString(StandardCharsets.UTF_8).lines()
            .filter(line -> line.contains("[extension]")).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(logs.contains("AUTH_KEY_ISSUED"));
        assertTrue(logs.contains("AUTH_CHECK"));
        assertTrue(logs.contains("LOGIN key_verified=true jennifer_auth=pending"));
        assertTrue(logs.contains("INVALID_KEY reason=mismatch"));
        assertTrue(logs.contains("store="));
        assertTrue(logs.contains("key_tag="));
        assertFalse(logs.contains(key));
        assertFalse(logs.contains("test-plugin-api-credential"));
        assertFalse(logs.contains(USER));
        assertFalse(logs.contains(DEVICE));
    }

    private String issue(String user, String device) throws Exception {
        URI uri = URI.create("/plugin/kbapi/authkey?user_id=" + encode(user) + "&device_id=" + encode(device)
            + "&token=test-plugin-api-credential");
        String key = http.perform(get(uri)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-KB-SSO-Version", "3.0.0"))
            .andExpect(header().string("X-KB-SSO-Build", "v2-memory-2"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(key.matches("[A-Za-z0-9_-]{43}"));
        return key;
    }

    private URI loginUrl(String user, String device, String wireKey) {
        return URI.create("/login/sso?user_id=" + encode(user) + "&device_id=" + encode(device) + "&auth_key=" + wireKey);
    }

    private String encode(String value) {
        // RFC 3986 query encoding avoids MockMvc's URI-parser treatment of form-style '+'.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Jennifer owns HTTP dispatch/session/account checks; this test invokes its real adapter boundary. */
    @RestController
    public static class JenniferLoginBoundary {
        private final KbLoginAdapter adapter = new KbLoginAdapter();
        private int calls;
        private String receivedKey;
        private String receivedUserId;
        private String receivedDeviceId;
        private UserData user;

        @GetMapping(value = "/login/sso", produces = "text/plain;charset=UTF-8")
        public ResponseEntity<String> login(HttpServletRequest request) {
            calls++;
            receivedKey = request.getParameter("auth_key");
            receivedUserId = request.getParameter("user_id");
            receivedDeviceId = request.getParameter("device_id");
            user = adapter.preHandle(request);
            if (user == null) return ResponseEntity.status(401).build();
            request.getSession().setAttribute("test-user", user.id);
            return ResponseEntity.ok(user.id);
        }

        @GetMapping("/test/logout")
        public ResponseEntity<Void> logout(HttpServletRequest request) {
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            return ResponseEntity.noContent().build();
        }
    }
}
