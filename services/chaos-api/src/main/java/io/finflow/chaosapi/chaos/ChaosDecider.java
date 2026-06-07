package io.finflow.chaosapi.chaos;

import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom; //Java's random number generator,
// specifically designed for multi-threaded applications (like web servers handling
// hundreds of users at once


/**
 * The "Dice Roller" for the fault injection system.
 *
 * What this does:
 * This class decides the fate of every incoming API request. It looks at the
 * live rules (like a 20% failure rate) and rolls virtual dice to determine if
 * a request should pass normally, crash with a 503 error, or hang for a few seconds.
 *
 * Why it is built this way:
 * By keeping this math completely separated from the web server code, it becomes
 * incredibly easy to write fast, reliable unit tests for it. Because it reads
 * directly from the live ChaosState memory, any rule changes made on the admin
 * dashboard take effect instantly on the very next roll.
 */
@Component
public class ChaosDecider {

    //creates a strict custom list of 3 possible answers
    public enum Outcome{PASS, FAIL_503, HANG}

    private final ChaosState state;

    //exact same memory ChaosState that the ChaosControlController is modifying
    //whenever new rules come in, Decider instantly uses those new numbers
    public ChaosDecider(ChaosState state){
        this.state = state;
    }


    //This is the method the Bouncer calls in the real application.
    // It generates two random numbers and passes them to the core logic:
    //nextInt(100): A number from 0 to 99 (simulating a 100-sided die).
    //nextDouble(): A decimal between 0.0 and 1.0 (like flipping a weighted coin).
    public Outcome decide(){
        return decide(ThreadLocalRandom.current().nextInt(100),
                ThreadLocalRandom.current().nextDouble());
    }

    Outcome decide(int roll, double split){
        if(!state.enabled()){
            return Outcome.PASS; //The Master Switch check. If chaos is disabled, skip the math and immediately let the request through.
        }
        if(roll >= state.faultRate()){
            return Outcome.PASS; //The Fault Rate check. If your fault rate is 20%, it means any roll from 0 to 19 is a failure.
                                 // If the dice rolls a 20 or higher, the user survives and the request passes.
        }


        //If the code reaches this line, the user has failed the previous check and will suffer a fault. But which one?
        //It uses the second random number (split). If your hang share is 0.5 (50%), and the decimal rolls lower than 0.5,
        //it returns HANG. If it rolls higher, it returns FAIL_503.
        return (split < state.hangShare()) ? Outcome.HANG : Outcome.FAIL_503;
    }

    //quick helper method so the Interceptor can ask how many milliseconds it should freeze the system for when a HANG outcome is returned.
    public long hangMillis(){
        return state.hangMillis();
    }
}
