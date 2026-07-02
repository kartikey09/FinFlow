package io.finflow.query.api;

import io.finflow.query.projection.commitment.CommitmentHealth;
import io.finflow.query.projection.commitment.CommitmentHealthRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/commitments?status=underutilized
 *
 * <p>Returns commitment health rows. Optional {@code status} filter matches
 * the projection's status column (case-insensitive on input).
 */
@RestController
@RequestMapping("/api/v1/commitments")
public class CommitmentController {

    private final CommitmentHealthRepository repository;
    private final String tenantId;

    public CommitmentController(CommitmentHealthRepository repository,
                                @Value("${finflow.tenant-id:default}") String tenantId) {
        this.repository = repository;
        this.tenantId = tenantId;
    }

    @GetMapping
    public List<CommitmentHealthDto> list(@RequestParam(name = "status", required = false) String status) {
        List<CommitmentHealth> rows = (status == null || status.isBlank())
                ? repository.findByTenantIdOrderByLatestFractionAsc(tenantId)
                : repository.findByTenantIdAndStatus(tenantId, status.trim().toUpperCase());
        return rows.stream().map(CommitmentController::toDto).toList();
    }

    private static CommitmentHealthDto toDto(CommitmentHealth h) {
        return new CommitmentHealthDto(
                h.getCommitmentId(), h.getVendor(), h.getAccountId(),
                h.getStatus(), h.getLastAlertType(), h.getLastAlertAt(),
                h.getLatestPeriodDay(), h.getLatestFraction(),
                h.getLatestUsedUsd(), h.getLatestAvailableUsd());
    }
}
