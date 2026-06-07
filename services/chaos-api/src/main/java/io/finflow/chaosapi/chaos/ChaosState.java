package io.finflow.chaosapi.chaos;

import org.springframework.stereotype.Component;

//special versions of Java variables designed for multithreaded environments
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * The live, mutable memory box (State) for the chaos engineering environment.
 *
 * What this does:
 * This class holds the active rules for fault injection. It grabs the initial default
 * settings from the configuration file (application.yml) when the server starts, but
 * holds them in unlocked, thread-safe variables that can be modified on the fly.
 *
 * Why it is built this way:
 * Configuration files are completely read-only after a Java application boots up.
 * If the Decider read directly from the config file, you would have to completely
 * shut down and restart the server just to change the fault rate from 20% to 50%.
 * By using this shared state object, the admin dashboard can update the rules in RAM,
 * and the Decider will instantly apply them to the very next network request with
 * zero downtime.
 */
@Component
public class ChaosState {

    //declares the 4 rules for the chaos environment
    //enabled and faultRate are Atomic - designed to be changed on the fly
    private final AtomicBoolean enabled;
    private final AtomicInteger faultRate;
    //you don't really need to change the length of a hang during a live demo,
    //so you save memory and complexity by leaving them as fixed values.
    private final double hangShare;
    private final long hangMillis;


    /**
     *
     * When Spring Boot creates this ChaosState object, it automatically injects your ChaosProperties-
     * (the locked object containing the values from your application.yml)
     * This constructor takes those locked values (like 20 for the fault rate)
     * and uses them to "seed" the starting values of your live memory.
     *
     */
    public ChaosState(ChaosProperties props){
        this.enabled = new AtomicBoolean(props.enabled()); //Atomic variables require calling .get() to extract the actual primitive value inside them.
        this.faultRate = new AtomicInteger(props.faultRate());
        this.hangShare = props.hangShare();
        this.hangMillis = props.hangMillis();
    }

    /**
     *The Getter Methods-
     *
     * What it does: Provides safe, read-only access to the current values.
     * The ChaosDecider calls these methods every time it needs to roll the dice.
     */
    public boolean enabled(){
        return enabled.get();
    }

    public int faultRate(){
        return faultRate.get();
    }

    public double hangShare(){
        return hangShare;
    }

    public long hangMillis(){
        return hangMillis;
    }


    /**
     * The Setter Methods-
     *
     * Provides the write access. The ChaosControlController calls these methods when you hit
     * the /chaos/rate?value=50 endpoint. It uses the .set() method on the Atomic objects to instantly update the live value.
     */
    public void setEnabled(boolean on){
        enabled.set(on);
    }

    public void setFaultRate(int rate){
        faultRate.set(rate);
    }

    /**
     * Why we use Atomic variables (AtomicBoolean, AtomicInteger) instead of standard primitives:
     *
     * Spring Boot's embedded Tomcat server is multithreaded. If 100 users hit the API at the
     * exact same millisecond, Tomcat spins up 100 separate threads to handle them simultaneously.
     *
     * If Thread A (the Controller) tries to write a new value to a standard boolean at the
     * exact same microsecond that Thread B (the Decider) is trying to read it, Java can
     * experience a "Race Condition," resulting in corrupted memory reads or application crashes.
     *
     * Atomic variables (from java.util.concurrent) solve this by locking the memory location
     * at the hardware level for a fraction of a nanosecond. If a thread is writing, all
     * reading threads are forced to wait until the write is 100% complete, guaranteeing
     * thread safety and preventing data corruption under heavy traffic.
     */
}
