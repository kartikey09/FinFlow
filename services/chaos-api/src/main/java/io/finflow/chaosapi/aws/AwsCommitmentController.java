package io.finflow.chaosapi.aws;

import io.finflow.chaosapi.aws.dto.PurchaseReservedInstancesRequest;
import io.finflow.chaosapi.aws.dto.PurchaseReservedInstancesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * This Controller accepts orders and returns a confirmation that a purchase was made.
 */


@RestController
@RequestMapping("/aws/ec2")    //separating AWS's billing API from its EC2(servers) API
public class AwsCommitmentController {
    private static final Logger log = LoggerFactory.getLogger(AwsCommitmentController.class);

    @PostMapping("/purchase-reserved-instances")
    public PurchaseReservedInstancesResponse purchase(   //returns the DTO we built
                @RequestBody PurchaseReservedInstancesRequest request){
        String reservationId = "ri-" + UUID.randomUUID().toString().substring(0,12);

        log.info("[AWS-CHAOS] purchase RI offering={} count={} -> reservationId={}",
                request.reservedInstancesOfferingId(),
                request.instanceCount(),
                reservationId);

        return new PurchaseReservedInstancesResponse(reservationId, "active");
    }
}
