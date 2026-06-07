package io.finflow.chaosapi.gcp;

import io.finflow.chaosapi.gcp.dto.CreateCommitmentRequest;
import io.finflow.chaosapi.gcp.dto.CreateCommitmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pretends to be the GCP Committed Use Discount (CUD) creation API. The saga's
 * gcp-adapter-worker (Week 4) POSTs here to "buy" a commitment as part of a
 * rebalance — the GCP analogue of AwsCommitmentController.
 *
 * Day 3: always succeeds, returns a fully-qualified commitment name.
 * Day 4: fault injection is added so ~20% of calls fail or hang.
 */

@RestController
@RequestMapping("/gcp/billing")
public class GcpCommitmentController {

    private static final Logger log = LoggerFactory.getLogger(GcpCommitmentController.class);

    @PostMapping("/commitments")
    public CreateCommitmentResponse createCommitment(
            @RequestBody CreateCommitmentRequest request) {

        // GCP resource-name convention: projects/{p}/regions/{r}/commitments/{id}
        String commitmentName = "projects/" + request.projectId()
                + "/regions/" + request.region()
                + "/commitments/cud-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("[GCP-CHAOS] create CUD project={} region={} plan={} {}x{} -> {}",
                request.projectId(), request.region(), request.plan(),
                request.amount(), request.resourceType(), commitmentName);

        return new CreateCommitmentResponse(commitmentName, "ACTIVE");
    }
}