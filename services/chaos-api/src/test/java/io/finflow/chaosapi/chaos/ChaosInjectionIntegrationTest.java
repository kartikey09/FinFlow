package io.finflow.chaosapi.chaos;

import io.finflow.chaosapi.ChaosApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test over real HTTP. Boots the full app and proves:
 *   - with chaos on, a vendor endpoint returns 503 (interceptor short-circuits)
 *   - actuator/health is NEVER faulted (outside the intercepted paths)
 *   - flipping chaos off restores normal 200s
 *
 * hang-share=0 is pinned here so a fault is ALWAYS a 503, never a 5s hang —
 * that makes "did not return 200" a deterministic assertion. Each test sets the
 * enabled/rate state it needs at its start, so JUnit execution order is
 * irrelevant (the shared context's ChaosState is mutable between methods).
 */
@SpringBootTest(
        classes = ChaosApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "chaos.hang-share=0" })
class ChaosInjectionIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void at100PercentFaultRate_vendorEndpointReturns503_butHealthSurvives() {
        rest.postForObject("/chaos/enable?on=true", null, String.class);
        rest.postForObject("/chaos/rate?value=100", null, String.class);

        ResponseEntity<String> aws = rest.getForEntity("/aws/cost-and-usage-report", String.class);
        assertThat(aws.getStatusCode().value())
                .as("AWS endpoint at 100%% fault rate (hang-share 0) should be 503")
                .isEqualTo(503);

        ResponseEntity<String> gcp = rest.getForEntity("/gcp/billing-export", String.class);
        assertThat(gcp.getStatusCode().value()).isEqualTo(503);

        // Health must remain up regardless of chaos
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(health.getBody()).contains("UP");
    }

    @Test
    void chaosDisabled_restoresNormalResponses() {
        rest.postForObject("/chaos/enable?on=false", null, String.class);

        ResponseEntity<String> aws = rest.getForEntity("/aws/cost-and-usage-report", String.class);
        assertThat(aws.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(aws.getBody()).contains("identity/LineItemId");

        ResponseEntity<String> gcp = rest.getForEntity("/gcp/billing-export", String.class);
        assertThat(gcp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gcp.getBody()).contains("billing_account_id");
    }

    @Test
    void controlEndpoint_reportsStatus() {
        String status = rest.getForObject("/chaos/status", String.class);
        assertThat(status).contains("enabled").contains("faultRate");
    }
}
