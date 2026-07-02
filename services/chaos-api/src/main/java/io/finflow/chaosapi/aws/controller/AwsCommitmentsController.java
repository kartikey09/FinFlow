package io.finflow.chaosapi.aws.controller;

import io.finflow.chaosapi.aws.dto.CommitmentActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AWS commitments rebalance endpoints — a stand-in for what would be the
 * real AWS control-plane API in production.
 *
 * <p>Every endpoint here has ONE job: return 200 with {@code success=true}
 * unless the existing {@code ChaosInterceptor} (registered on {@code /aws/**})
 * decides to inject a 503 or a 5s hang. The whole point is to give the
 * adapter workers something to call that fails in interesting ways.
 *
 * <p>The five endpoints mirror {@code SagaStep}:
 * <ul>
 *   <li>{@code POST /aws/commitments/{id}/lock} — ACQUIRE_LOCK</li>
 *   <li>{@code POST /aws/commitments/{id}/verify} — VERIFY_COMMITMENT</li>
 *   <li>{@code POST /aws/commitments/{id}/reserve} — RESERVE_TARGET</li>
 *   <li>{@code POST /aws/commitments/{id}/release} — RELEASE_SOURCE</li>
 *   <li>{@code POST /aws/commitments/{id}/update-ledger} — UPDATE_LEDGER</li>
 * </ul>
 *
 * <p>Undo commands (compensation) reuse the SAME endpoints — the adapter
 * decides based on the command's {@code direction} field which endpoint to
 * call, and the chaos-api doesn't care. In a real system these would be
 * separate paths (lock vs. unlock); for the demo, the outcome is the same.
 */
@RestController
@RequestMapping("/aws/commitments")
public class AwsCommitmentsController {

    private static final Logger log = LoggerFactory.getLogger(AwsCommitmentsController.class);

    @PostMapping("/{commitmentId}/lock")
    public CommitmentActionResponse lock(@PathVariable String commitmentId) {
        log.info("AWS commitments/lock  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "lock");
    }

    @PostMapping("/{commitmentId}/verify")
    public CommitmentActionResponse verify(@PathVariable String commitmentId) {
        log.info("AWS commitments/verify  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "verify");
    }

    @PostMapping("/{commitmentId}/reserve")
    public CommitmentActionResponse reserve(@PathVariable String commitmentId) {
        log.info("AWS commitments/reserve  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "reserve");
    }

    @PostMapping("/{commitmentId}/release")
    public CommitmentActionResponse release(@PathVariable String commitmentId) {
        log.info("AWS commitments/release  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "release");
    }

    @PostMapping("/{commitmentId}/update-ledger")
    public CommitmentActionResponse updateLedger(@PathVariable String commitmentId) {
        log.info("AWS commitments/update-ledger  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "update-ledger");
    }
}
