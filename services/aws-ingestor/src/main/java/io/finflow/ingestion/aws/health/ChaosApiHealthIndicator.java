package io.finflow.ingestion.aws.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Contributes a "chaosApi" component to /actuator/health that pings the
 * upstream's own health endpoint. Because ChaosWebConfig exempts /actuator
 * from fault injection, this ping is reliable — it reflects genuine
 * reachability, not the injected chaos.
 *
 * (In production you'd cache this or move it to a separate health group so a
 * slow upstream can't slow every probe — noted as a deliberate shell shortcut.)
 */
@Component
public class ChaosApiHealthIndicator implements HealthIndicator {

    private final RestClient chaosApiClient;

    public ChaosApiHealthIndicator(RestClient chaosApiClient) {
        this.chaosApiClient = chaosApiClient;
    }

    @Override
    public Health health() {
        try {
            String body = chaosApiClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .body(String.class);
            if (body != null && body.contains("UP")) {
                return Health.up().withDetail("chaosApi", "reachable").build();
            }
            return Health.down().withDetail("chaosApi", "unexpected response").build();
        } catch (Exception e) {
            return Health.down().withDetail("chaosApi", e.getMessage()).build();
        }
    }
}
