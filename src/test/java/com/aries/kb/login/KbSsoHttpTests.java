package com.aries.kb.login;

import com.aries.kb.api.KbApiController;
import com.aries.kb.auth.AuthKeyGenerator;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.Assert.*;

/** Exercise actual GET query decoding by an embedded Servlet container, without customer access. */
public class KbSsoHttpTests {
    private int port;

    @Test
    public void actualGetApiAndSsoRequestsHandleFormEncodingAndLegacyDoubleEncoding() {
        try (ConfigurableApplicationContext server = new SpringApplicationBuilder(Server.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "server.address=127.0.0.1", "spring.main.banner-mode=off").run()) {
            port = ((ServletWebServerApplicationContext) server).getWebServer().getPort();
            verifyGetFlow();
        }
    }

    private void verifyGetFlow() {
        String user = "은행 사용자+/%=";
        String device = "단말 +&=";
        String params = "user_id=" + encode(user) + "&device_id=" + encode(device);
        RestTemplate client = new RestTemplate();
        ResponseEntity<String> api = client.getForEntity(uri("/plugin/kbapi/authkey?" + params
            + "&token=test-plugin-api-credential"), String.class);
        assertEquals(200, api.getStatusCodeValue());
        assertEquals("v2-memory-2", api.getHeaders().getFirst("X-KB-SSO-Build"));
        assertTrue(api.getBody().matches("[A-Za-z0-9_-]{43}"));
        assertEquals("guest", client.getForObject(uri("/login/sso?" + params + "&auth_key=" + encode(api.getBody())), String.class));

        byte[] bytes = new byte[16];
        Arrays.fill(bytes, (byte) 0xff);
        bytes[0] = (byte) 0xfb;
        String raw = Base64.getEncoder().encodeToString(bytes);
        String legacyApiResponse = encode(raw);
        KbLoginAdapter.Companion.getAUTH_KEYS().put(AuthKeyGenerator.identityKey(user, device), legacyApiResponse, 10000L);
        assertEquals("guest", client.getForObject(uri("/login/sso?" + params + "&auth_key=" + legacyApiResponse), String.class));
        assertEquals("guest", client.getForObject(uri("/login/sso?" + params + "&auth_key=" + encode(legacyApiResponse)), String.class));
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({KbApiController.class, KbSsoFlowTests.JenniferLoginBoundary.class})
    static class Server { }
}
