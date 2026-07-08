package io.finflow.chaosapi.chaos;

import org.springframework.stereotype.Component;

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
     * Day 20: per-endpoint chaos targeting.
     *
     * <p>The saga compensation test needs to fail ONE specific step (e.g. step 3,
     * RESERVE_TARGET) at 100% while every other step stays healthy. A single global
     * fault rate can't express that — set it to 100% and steps 1 and 2 fail too, so
     * the saga never reaches step 3. The fix is a substring filter: when
     * {@code targetPathContains} is set, only request URIs containing that substring
     * see {@code targetFaultRate}; everything else keeps seeing the global (background)
     * {@link #faultRate}. Set {@code targetPathContains="reserve"} +
     * {@code targetFaultRate=100} to fail only the reserve endpoint.
     */
    private volatile String targetPathContains;
    private final AtomicInteger targetFaultRate = new AtomicInteger(0);


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


    // ---- Day 20: per-endpoint targeting -------------------------------------

    public String targetPathContains(){
        return targetPathContains;
    }

    public int targetFaultRate(){
        return targetFaultRate.get();
    }

    public void setTargetPathContains(String path){
        this.targetPathContains = path;
    }

    public void setTargetFaultRate(int rate){
        targetFaultRate.set(rate);
    }

    /** Clears the target filter; all traffic falls back to the global fault rate. */
    public void clearTarget(){
        this.targetPathContains = null;
        targetFaultRate.set(0);
    }

    /**
     * The fault rate that actually applies to a given request URI.
     *
     * <p>Returns 0 when chaos is disabled (master switch). Otherwise, if a target
     * filter is active AND the URI matches it, returns {@link #targetFaultRate};
     * every other request gets the global background {@link #faultRate}.
     */
    public int effectiveFaultRate(String requestUri){
        if(!enabled.get()){
            return 0;
        }
        String tp = targetPathContains;
        if(tp != null && !tp.isBlank() && requestUri != null && requestUri.contains(tp)){
            return targetFaultRate.get();
        }
        return faultRate.get();
    }
}
