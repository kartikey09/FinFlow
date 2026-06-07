package io.finflow.chaosapi.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A small admin dashboard used to turn chaos on/off or change the failure rate
 * while the server is running. No restart needed!
 *
 * It uses the "/chaos" URL so it doesn't accidentally break itself (the fault
 * injector only targets "/aws" and "/gcp" traffic).
 *
 * When you change settings here, it updates the live memory (ChaosState). This
 * means the very next API request will instantly use your new rules.
 */

@RestController
@RequestMapping("/chaos")
public class ChaosControlController{

    //Sets up the logger to print distinct [CHAOS-CONTROL] messages to the terminal when the settings are changed.
    private static final Logger log = LoggerFactory.getLogger(ChaosControlController.class);

    private final ChaosState state;

    //passing the mutable memory instead of the immutable config file (ChaosProperties)
    //because this controller and ChaosDEcider share the exact same ChaosState object in RAM,
    //any change made here is instantly seen by the Decider on the very next request
    public ChaosControlController(ChaosState state){
        this.state = state;
    }


    /**
     * Endpoint 1 - checking the status
     *
     * It looks into the server's live memory (ChaosState) and returns a JSON snapshot of the current rules:
     * is chaos currently turned on? What is the failure rate? How long are the hangs?
     * Why it's needed: In a complex microservices setup, you should never have to guess what state a system is in.
     * If your downstream ingestor starts failing out of nowhere, you can hit this endpoint to confirm,
     * "someone left the chaos switch turned on at a 50% failure rate."
     */
    @GetMapping("/status")    //when GET /chaos/status is called it takes the current 4 values of the ChaosState memomry and makes a java map
    public Map<String, Object> status(){
        return Map.of(   //Map.of automatically converts a java map into a formatted JSON object
                "enabled", state.enabled(),
                "faultRate", state.faultRate(),
                "hangShare", state.hangShare(),
                "hangMillis", state.hangMillis()
        );
    }


    /**Endpoint 2 - ON/OFF switch for fault injection
     *
     * If you pass false, it instantly bypasses all fault logic. The ChaosDecider will automatically let
     * 100% of traffic pass through successfully, completely ignoring the fault rate percentage.
     * When you are testing, you first want to prove that your FinFlow system works perfectly under normal
     * conditions. You hit this endpoint to turn chaos off, run some clean traffic, and then hit ?on=true to
     * instantly plunge the system into chaos so you can watch your safety nets deploy.
     *
     */
    @PostMapping('/enable')
    public Map<String, Object> enable(@RequestParam(defaultValue = "true") boolean on){
        state.setEnabled(on);
        log.warn("[CHAOS_CONTROL] chaos enabled set to {}", on);
        return status();
    }

    @PostMapping("/rate")  //changes the exact percentage of requests that will be sabotaged by the Bouncer
    public Map<String, Object> rate(@RequestParam int value){
        if(value<0 || value>100){
            throw new IllegalArgumentException("Rate must be 0-100");
        }
        state.setFaultRate(value);
        log.warn("[CHAOS-CONTROL] fault rate set to {}%", value);
        return status();
    }

    /**
     *
     * "If I already set fault-rate: 20 in my application.yml, why do I need web endpoints to change it?"
     *
     * The answer is zero-downtime reconfiguration.
     *
     * Configuration files like application.yml are read exactly once when the Spring Boot application starts up.
     * If you didn't have these endpoints, changing the fault rate from 20% to 50% would require you to completely shut down the server,
     * edit the text file, and wait 5 seconds for the server to boot back up.
     * By building these control endpoints that mutate a shared ChaosState object in RAM, you can dynamically alter the behavior
     * of a running application in milliseconds without dropping a single active network connection. This is exactly how feature
     * flags and live configuration changes work in massive enterprise systems!
     */
}
