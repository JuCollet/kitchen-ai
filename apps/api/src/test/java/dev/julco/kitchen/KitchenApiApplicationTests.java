package dev.julco.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KitchenApiApplicationTests {

    @Value("${local.server.port}")
    private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // Return 4xx/5xx responses instead of throwing, so tests can assert on them.
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    private ResponseEntity<String> get(String path) {
        return http.get().uri(path).retrieve().toEntity(String.class);
    }

    @Test
    void healthIsUpWithoutDetails() {
        var response = get("/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");
    }

    @Test
    void probesAreExposed() {
        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        for (var endpoint : new String[] {"env", "beans", "heapdump", "configprops", "mappings"}) {
            // 401 without a session; never exposed, even with one.
            assertThat(get("/actuator/" + endpoint).getStatusCode().is4xxClientError())
                    .as(endpoint)
                    .isTrue();
        }
    }

    /** Real HTTP request: the site reads this cookie and sends it back in the X-XSRF-TOKEN header. */
    @Test
    void csrfCookieIsReadableByTheSite() {
        var response = get("/api/csrf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().get("Set-Cookie"))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly"));
    }

    @Test
    void apiCallsWithoutSessionAreRejected() {
        assertThat(get("/api/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
