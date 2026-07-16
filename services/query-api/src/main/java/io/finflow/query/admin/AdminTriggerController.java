package io.finflow.query.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * POST /api/v1/admin/trigger-ingest?vendor=aws
 *
 * <p>Convenience endpoint the dashboard's "Trigger billing pull" button hits.
 * It forwards a poke to the appropriate ingestor. In the demo this is what
 * makes the numbers move visibly — you press the button, an ingestor cycles,
 * events flow, the projections update, the UI polls, the chart changes.
 *
 * <p>NOTE: the ingestors already poll on a fixed schedule (~30s). This endpoint
 * is present because the plan calls for it and it makes the demo interactive.
 * If the ingestor doesn't expose a {@code /trigger} endpoint yet, this reports
 * a friendly "not-yet-wired" message rather than 500 — the polling loop is
 * still doing its job. Wiring the actual trigger endpoint on the ingestor side
 * is a &lt;10-line addition on Days 11/12 (a {@code @PostMapping} that calls
 * {@code AwsIngestScheduler.poll()} directly).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminTriggerController {

    private static final Logger log = LoggerFactory.getLogger(AdminTriggerController.class);

    /**
     * Day 24: built from Spring's auto-configured RestClient.Builder, NOT the static
     * factory. Only the injected builder carries ObservationRestClientCustomizer,
     * which creates the HTTP client span and injects the traceparent header. The
     * static factory gives you an UNINSTRUMENTED client: the call still works, it
     * just never shows up in a trace.
     */
    private final RestClient restClient;
    private final String awsBaseUrl;
    private final String gcpBaseUrl;

    public AdminTriggerController(RestClient.Builder restClientBuilder,               // Day 24
                                  @Value("${finflow.aws-ingestor.base-url}") String awsBaseUrl,
                                  @Value("${finflow.gcp-ingestor.base-url}") String gcpBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.awsBaseUrl = awsBaseUrl;
        this.gcpBaseUrl = gcpBaseUrl;
    }

    @PostMapping("/trigger-ingest")
    public ResponseEntity<Map<String, Object>> triggerIngest(
            @RequestParam(name = "vendor", defaultValue = "aws") String vendor) {
        String base = switch (vendor.toLowerCase()) {
            case "aws" -> awsBaseUrl;
            case "gcp" -> gcpBaseUrl;
            default    -> null;
        };
        if (base == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "message", "unknown vendor: " + vendor));
        }
        try {
            // We call the ingestor's /actuator/health as a liveness signal, since the
            // /trigger endpoint may not exist yet. Real behavior is the scheduled poll.
            restClient.get().uri(base + "/actuator/health").retrieve().toBodilessEntity();
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "vendor", vendor,
                    "message", "ingestor is up; scheduled poll will run within its poll-interval",
                    "note", "add POST /trigger on the ingestor to force an immediate cycle"));
        } catch (Exception e) {
            log.warn("trigger-ingest for vendor={} could not reach {}: {}", vendor, base, e.toString());
            return ResponseEntity.ok(Map.of(
                    "ok", false, "vendor", vendor,
                    "message", "ingestor unreachable at " + base));
        }
    }
}
