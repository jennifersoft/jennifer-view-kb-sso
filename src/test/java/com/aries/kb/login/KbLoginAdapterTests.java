package com.aries.kb.login;

import com.aries.extension.data.UserData;
import com.aries.extension.util.PropertyUtil;
import com.aries.kb.api.KbApiController;
import com.aries.kb.auth.AuthKeyGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class KbLoginAdapterTests {

    @Test
    public void acceptsRepeatedValidationOfTheCurrentKeyWithinItsLifetimeLikeV2() {
        String token = new KbApiController().createAuthKey("user-1", "device-1").getBody();
        KbLoginAdapter adapter = new KbLoginAdapter();
        MockHttpServletRequest request = loginRequest("user-1", "device-1", token);

        UserData firstAttempt = adapter.preHandle(request);
        UserData replayAttempt = adapter.preHandle(request);

        assertNotNull(firstAttempt);
        assertEquals("guest", firstAttempt.id);
        assertEquals("guest", firstAttempt.password);
        assertNotNull("Jennifer may validate the same login more than once within 10 seconds", replayAttempt);
    }

    @Test
    public void usesConfiguredJenniferCredentialsAfterValidatingTheCustomerIdentity() {
        try (MockedStatic<PropertyUtil> options = mockStatic(PropertyUtil.class, CALLS_REAL_METHODS)) {
            options.when(() -> PropertyUtil.getValue("kb_login", "KB_JENNIFER_ID", "guest"))
                .thenReturn("jennifer-reader");
            options.when(() -> PropertyUtil.getValue("kb_login", "KB_JENNIFER_PASSWORD", "guest"))
                .thenReturn("test-configured-password");
            String key = new KbApiController().createAuthKey("customer-user", "customer-device").getBody();
            KbLoginAdapter adapter = new KbLoginAdapter();

            UserData result = adapter.preHandle(loginRequest("customer-user", "customer-device", key));

            assertNotNull(result);
            assertEquals("jennifer-reader", result.id);
            assertEquals("test-configured-password", result.password);
            assertNull("The configured login account must not replace the identity bound to the key",
                adapter.preHandle(loginRequest("jennifer-reader", "customer-device", key)));
        }
    }

    @Test
    public void wrongTokenDoesNotConsumeTheValidToken() {
        String token = new KbApiController().createAuthKey("user-wrong-token", "device-1").getBody();
        KbLoginAdapter adapter = new KbLoginAdapter();

        assertNull(adapter.preHandle(loginRequest("user-wrong-token", "device-1", "wrong-token")));
        assertNotNull(adapter.preHandle(loginRequest("user-wrong-token", "device-1", token)));
    }

    @Test
    public void userMismatchDoesNotConsumeTheValidToken() {
        String token = new KbApiController().createAuthKey("user-owner", "device-1").getBody();
        KbLoginAdapter adapter = new KbLoginAdapter();

        assertNull(adapter.preHandle(loginRequest("user-other", "device-1", token)));
        assertNotNull(adapter.preHandle(loginRequest("user-owner", "device-1", token)));
    }

    @Test
    public void deviceMismatchDoesNotConsumeTheValidToken() {
        String token = new KbApiController().createAuthKey("user-device", "device-owner").getBody();
        KbLoginAdapter adapter = new KbLoginAdapter();

        assertNull(adapter.preHandle(loginRequest("user-device", "device-other", token)));
        assertNotNull(adapter.preHandle(loginRequest("user-device", "device-owner", token)));
    }

    @Test
    public void replacementTokenInvalidatesThePreviousToken() {
        KbApiController controller = new KbApiController();
        String previous = controller.createAuthKey("user-replacement", "device-1").getBody();
        KbLoginAdapter.Companion.getAUTH_KEYS().put(
            AuthKeyGenerator.identityKey("user-replacement", "device-1"), previous, 0L);
        String replacement = controller.createAuthKey("user-replacement", "device-1").getBody();
        KbLoginAdapter adapter = new KbLoginAdapter();

        assertNull(adapter.preHandle(loginRequest("user-replacement", "device-1", previous)));
        assertNotNull(adapter.preHandle(loginRequest("user-replacement", "device-1", replacement)));
    }

    @Test
    public void rejectsRequestsMissingAnyRequiredParameter() {
        KbLoginAdapter adapter = new KbLoginAdapter();
        MockHttpServletRequest missingUser = loginRequest(null, "device-1", "token");
        MockHttpServletRequest missingDevice = loginRequest("user-params", null, "token");
        MockHttpServletRequest missingToken = loginRequest("user-params", "device-1", null);

        assertNull(adapter.preHandle(missingUser));
        assertNull(adapter.preHandle(missingDevice));
        assertNull(adapter.preHandle(missingToken));
    }

    private MockHttpServletRequest loginRequest(String userId, String deviceId, String authKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.setParameter("user_id", userId);
        }
        if (deviceId != null) {
            request.setParameter("device_id", deviceId);
        }
        if (authKey != null) {
            request.setParameter("auth_key", authKey);
        }
        return request;
    }
}
