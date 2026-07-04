package io.finflow.chaosapi.gcp.controller;

import io.finflow.chaosapi.gcp.dto.CommitmentActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The GCP commitments rebalance endpoints — a stand-in for what would be the
 * real GCP Cloud Billing API in production.
 *
 * <p>Symmetric to {@code AwsCommitmentsController}: five POST endpoints, each
 * returning {@code success=true} unless the existing {@code ChaosInterceptor}
 * (which already matches {@code /gcp/**}) injects a 503 or a 5s hang.
 *
 * <p>Endpoints mirror the same five saga steps:
 * <ul>
 *   <li>{@code POST /gcp/billing/commitments/{id}/lock} — ACQUIRE_LOCK</li>
 *   <li>{@code POST /gcp/billing/commitments/{id}/verify} — VERIFY_COMMITMENT</li>
 *   <li>{@code POST /gcp/billing/commitments/{id}/reserve} — RESERVE_TARGET</li>
 *   <li>{@code POST /gcp/billing/commitments/{id}/release} — RELEASE_SOURCE</li>
 *   <li>{@code POST /gcp/billing/commitments/{id}/update-ledger} — UPDATE_LEDGER</li>
 * </ul>
 *
 * <p>The path prefix follows what the plan calls out: {@code /gcp/billing/commitments}.
 * That's slightly deeper than AWS's {@code /aws/commitments} but the chaos
 * interceptor's {@code /gcp/**} matcher catches all of it.
 */
@RestController
@RequestMapping("/gcp/billing/commitments")
public class GcpCommitmentsController {

    private static final Logger log = LoggerFactory.getLogger(GcpCommitmentsController.class);

    @PostMapping("/{commitmentId}/lock")
    public CommitmentActionResponse lock(@PathVariable String commitmentId) {
        log.info("GCP billing/commitments/lock  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "lock");
    }

    @PostMapping("/{commitmentId}/verify")
    public CommitmentActionResponse verify(@PathVariable String commitmentId) {
        log.info("GCP billing/commitments/verify  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "verify");
    }

    @PostMapping("/{commitmentId}/reserve")
    public CommitmentActionResponse reserve(@PathVariable String commitmentId) {
        log.info("GCP billing/commitments/reserve  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "reserve");
    }

    @PostMapping("/{commitmentId}/release")
    public CommitmentActionResponse release(@PathVariable String commitmentId) {
        log.info("GCP billing/commitments/release  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "release");
    }

    @PostMapping("/{commitmentId}/update-ledger")
    public CommitmentActionResponse updateLedger(@PathVariable String commitmentId) {
        log.info("GCP billing/commitments/update-ledger  {}", commitmentId);
        return new CommitmentActionResponse(true, commitmentId, "update-ledger");
    }
}
