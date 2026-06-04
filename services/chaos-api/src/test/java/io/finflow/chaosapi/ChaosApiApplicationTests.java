package io.finflow.chaosapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context smoke test. RANDOM_PORT starts the embedded server and wires
 * TestRestTemplate to it, so these calls go over real HTTP — the same path the
 * Week-3 ingestor will take.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChaosApiApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void contextLoads() {
        // Fails if the context can't wire — missing bean, broken @PostConstruct
        // data load, or two controllers claiming the same path.
    }

    @Test
    void healthEndpointIsUp() {
        String body = rest.getForObject("/actuator/health", String.class);
        assertThat(body).contains("UP");
    }

    @Test
    void awsBillingEndpoint_answersOverRealHttp_withCurFieldNames() {
        String body = rest.getForObject("/aws/cost-and-usage-report", String.class);
        assertThat(body)
                .contains("identity/LineItemId")
                .contains("lineItem/LineItemType");
    }

    @Test
    void awsPurchaseEndpoint_answersOverRealHttp() {
        String response = rest.postForObject(
                "/aws/ec2/purchase-reserved-instances",
                new PurchaseBody("offer-abc", 10),
                String.class);
        assertThat(response).contains("reservationId").contains("active");
    }

    private record PurchaseBody(String reservedInstancesOfferingId, Integer instanceCount) {}
}
