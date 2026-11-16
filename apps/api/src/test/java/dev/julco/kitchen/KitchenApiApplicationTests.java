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
            assertThat(get("/actuator/" + endpoint).getStatusCode())
                    .as(endpoint)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
