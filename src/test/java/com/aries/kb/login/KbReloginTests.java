package com.aries.kb.login;

import com.aries.extension.data.UserData;
import com.aries.kb.api.KbApiController;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class KbReloginTests {

    @Test
    public void threeLoginsAfterSessionInvalidationUseDistinctSingleUseTokens() throws Exception {
        MockMvc api = MockMvcBuilders.standaloneSetup(new KbApiController()).build();
        KbLoginAdapter adapter = new KbLoginAdapter();
        List<String> usedTokens = new ArrayList<>();

        // Each request uses the same identity and API credential, without waiting for expiry.
        for (int loginNumber = 1; loginNumber <= 3; loginNumber++) {
            String token = api.perform(get("/plugin/kbapi/authkey")
                    .param("user_id", "user-relogin")
                    .param("device_id", "device-relogin")
                    .param("token", "test-plugin-api-credential"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andReturn().getResponse().getContentAsString();

            for (String previous : usedTokens) {
                assertNotEquals("Each login must receive a fresh auth_key", previous, token);
                assertNull("An old key must not authenticate after issuing a new key",
                    adapter.preHandle(loginRequest(previous)));
            }

            MockHttpServletRequest request = loginRequest(token);
            UserData user = adapter.preHandle(request);
            assertNotNull("Fresh key must authenticate login " + loginNumber, user);
            assertEquals("user-relogin", user.id);

            // Jennifer owns login/logout; simulate its session invalidation at this boundary.
            request.getSession(false).invalidate();
            assertNull("Ending the session must not make the consumed key reusable",
                adapter.preHandle(loginRequest(token)));
            usedTokens.add(token);
        }

        for (String previous : usedTokens) {
            assertNull("All three consumed keys must remain unusable",
                adapter.preHandle(loginRequest(previous)));
        }
    }

    private MockHttpServletRequest loginRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/sso");
        request.setParameter("user_id", "user-relogin");
        request.setParameter("device_id", "device-relogin");
        request.setParameter("auth_key", token);
        request.setSession(new MockHttpSession());
        return request;
    }
}
