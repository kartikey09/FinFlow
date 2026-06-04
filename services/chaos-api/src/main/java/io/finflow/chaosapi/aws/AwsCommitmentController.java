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
/mode */


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


//How parameter to object conversion happens and vice versa
/*
 * Phase 1: Receiving the Request (JSON → Java Object)
 *
 * The Network Call: A client (or your Saga Orchestrator) sends an HTTP POST request to
 * /aws/ec2/purchase-reserved-instances. The body of this request is just a raw string of
 * text formatted as JSON, and the HTTP headers include Content-Type: application/json.
 *
 * The Interception: Spring's front controller (DispatcherServlet) receives the HTTP request
 * and routes it to your purchase method.
 *
 * The Trigger (@RequestBody): Spring sees the @RequestBody annotation next to your
 * PurchaseReservedInstancesRequest parameter. This annotation acts as a strict instruction:
 * "Do not pass me a raw string; convert the incoming HTTP body into this specific Java type."
 *
 * The Deserialization (Jackson): Spring delegates the raw JSON string to Jackson.
 *   - Jackson inspects the JSON keys (e.g., "instanceCount", "reservedInstancesOfferingId").
 *   - Because the project is compiled using Java 21 with the -parameters compiler flag enabled,
 *     Jackson can map those JSON keys directly to the constructor arguments of your Java Record
 *     (or class) natively. It does not need a default no-argument constructor, nor does it
 *     require explicit @JsonProperty annotations.
 *   - Jackson instantiates the PurchaseReservedInstancesRequest object, populates it with the
 *     data, and hands it to your method execution.
 *
 * Phase 2: Sending the Response (Java Object → JSON)
 *
 * The Method Return: Your business logic executes, and you return a newly instantiated Java
 * object: new PurchaseReservedInstancesResponse(reservationId, "active").
 *
 * The Trigger (@RestController): Normally, Spring controllers try to take returned objects and
 * render them into HTML templates. However, your class is annotated with @RestController.
 * Under the hood, @RestController is a convenience annotation that combines @Controller and
 * @ResponseBody.
 *   - @ResponseBody tells Spring: "Take the exact object returned by this method and write it
 *     directly into the HTTP response body."
 *
 * The Serialization (Jackson): Spring intercepts the returned PurchaseReservedInstancesResponse
 * object and hands it back to Jackson.
 *   - Jackson inspects the object, calls its accessors (the getters or Record field methods),
 *     and translates the Java memory references back into a raw JSON string.
 *
 * The Network Response: Spring takes that newly minted JSON string, attaches an HTTP 200 OK
 * status code, sets the Content-Type: application/json header, and sends it back across the
 * network to the client.
 */

