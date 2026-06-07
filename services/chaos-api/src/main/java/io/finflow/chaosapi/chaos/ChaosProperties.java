package io.finflow.chaosapi.chaos;

//importing the specific Spring Boot annotation required to link Java code to Yaml file
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The blueprint that reads configuration settings from your application.yml file.
 *
 * What this does:
 * This record reads the settings under the "chaos:" block in your configuration file
 * and safely converts them into usable Java variables.
 *
 * @param enabled       master switch; false = every call succeeds (Day 2/3 behavior)
 * @param faultRate     percent of requests that get a fault, 0-100
 * @param hangShare     of the faulted requests, the fraction that hang vs 503
 *                      (e.g. 0.5 = half hang, half return 503)
 * @param hangMillis    how long a "hang" sleeps before returning
 *
 * Why it is built this way:
 * 1. No Hardcoding: By reading from a file instead of hardcoding numbers into Java,
 * you can easily change the behavior of your system without rewriting code.
 * 2. Protection: The constructor includes safety checks. If someone accidentally sets
 * a fault rate greater than 100 or less than 0, the application will immediately
 * crash on startup with a clear error message instead of causing hidden bugs later.
 */


/**
 * It tells Spring Boot: "When the app starts up, open application.yml, loof for the block that starts with chaos:
 * and automatically push those values into the variables below.
 */
@ConfigurationProperties(prefix = "chaos")
public record ChaosProperties ( //defining a native Java record - perfect for holding immutable data
    boolean enabled,
    int faultRate,
    double hangShare,
    long hangMillis //variables here must match YAML keys exactly
) {
    public ChaosProperties{
        /**
         * This is a special feature of Java Records called a "compact constructor." Notice there are no () after ChaosProperties.
         * It allows you to run logic after the variables are assigned, without having to write boilerplate code like this.enabled = enabled;
         */

        /**
         * What it does -
         * This is defensive programming. It checks the numbers coming from the YAML file. If someone accidentally typed
         * fault-rate: 500 in the YAML, these if statements will catch it and immediately crash the application with a clear error message.
         */
        if(faultRate<0 || faultRate>100){
            throw new IllegalArgumentException("Chaos.fault-rate must be between 0-100, it was " + faultRate);
        }
        if(hangShare<0.0 || hangShare>1.0){
            throw new IllegalArgumentException("Chaos,hang-share must be between 0.0-1.0, it was " + hangShare);
        }
    }
}
