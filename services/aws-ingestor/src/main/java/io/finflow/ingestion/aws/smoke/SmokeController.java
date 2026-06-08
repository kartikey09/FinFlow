package io.finflow.ingestion.aws.smoke;

import io.finflow.ingestion.aws.cursor.PollCursor;
import io.finflow.ingestion.aws.cursor.PollCursorRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * DEV-ONLY smoke surface under /internal/smoke. Lets the Week-1 smoke script
 * drive a Kafka round-trip and inspect the persisted cursor over plain HTTP.
 * Deleted when Day 11's real poll loop arrives.
 */
@RestController
@RequestMapping("/internal/smoke")
public class SmokeController {

    private final SmokeService smokeService;
    private final PollCursorRepository cursors;

    public SmokeController(SmokeService smokeService, PollCursorRepository cursors) {
        this.smokeService = smokeService;
        this.cursors = cursors;
    }

    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestParam(defaultValue = "hello-finflow") String payload)
            throws InterruptedException {
        long ms = smokeService.publishAndAwait(payload);
        return Map.of("status", "ok", "roundTripMillis", ms, "topic", SmokeService.TOPIC);
    }

    @GetMapping("/cursor")
    public List<PollCursor> cursor() {
        return cursors.findAll();
    }
}
