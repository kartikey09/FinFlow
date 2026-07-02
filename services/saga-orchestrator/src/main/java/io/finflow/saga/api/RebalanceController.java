package io.finflow.saga.api;

import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.orchestration.SagaOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * The saga's edge of the world.
 *
 * <ul>
 *   <li>{@code POST /sagas/rebalance} — kicks off a rebalance from the dashboard.
 *       Body carries a {@code correlationId} the caller controls so retries by
 *       the same user click don't spawn multiple sagas.</li>
 *   <li>{@code GET /sagas/{id}} — small read for the UI to poll while the saga
 *       is in flight (Day 15's dashboard will use this on Day 20 when the
 *       rebalance button is wired).</li>
 * </ul>
 *
 * <p>These endpoints are DELIBERATELY thin — they exist to authorize the input
 * shape and hand off to {@link SagaOrchestrationService}. All the transaction
 * boundaries and orchestration logic live in the service.
 */
@RestController
@RequestMapping("/sagas")
public class RebalanceController {

    private final SagaOrchestrationService orchestrationService;

    public RebalanceController(SagaOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/rebalance")
    public ResponseEntity<RebalanceResponse> start(@RequestBody RebalanceRequest request) {
        if (request == null || request.correlationId() == null || request.correlationId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SagaInstance saga = orchestrationService.startRebalance(request.correlationId());
        return ResponseEntity.accepted().body(RebalanceResponse.of(saga));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RebalanceResponse> get(@PathVariable("id") UUID id) {
        Optional<SagaInstance> saga = orchestrationService.findById(id);
        return saga.map(s -> ResponseEntity.ok(RebalanceResponse.of(s)))
                   .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
