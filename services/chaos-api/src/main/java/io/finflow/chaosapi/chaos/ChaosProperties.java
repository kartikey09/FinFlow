package io.finflow.chaosapi.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos")
public record ChaosProperties (
    boolean enabled,
    int faultRate,
    double hangShare,
    long hangMillis
) {
    public ChaosProperties{
        if(faultRate<0 || faultRate>100){
            throw new IllegalArgumentException("Chaos.fault-rate must be between 0-100, it was " + faultRate);
        }
        if(hangShare<0.0 || hangShare>1.0){
            throw new IllegalArgumentException("Chaos,hang-share must be between 0.0-1.0, it was " + hangShare);
        }
    }
}
