package io.finflow.chaosapi.chaos;

//EnableConfigurationProperties - Activates ChaosProperties binding from application.yml
//without this it wouldn't know how to link chaosProperties to application.yml file
// activates that YAML-to-Java translation
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The "Manager" that wires the fault injection system into the web server.
 *
 * What this does:
 * This configuration file does two things at startup:
 * 1. It activates the ChaosProperties so the app reads your application.yml settings.
 * 2. It registers the ChaosInterceptor (the Bouncer) into the web traffic pipeline.
 *
 * Why it is built this way:
 * By using the `.addPathPatterns()` method, we explicitly draw a boundary around
 * the chaos. We tell the Bouncer to ONLY attack requests going to the fake AWS
 * and GCP endpoints. This guarantees that our internal control dashboard (/chaos)
 * and our vital infrastructure health checks (/actuator/health) remain 100%
 * reliable and untouched by the fault injection.
 */
@Configuration
@EnableConfigurationProperties(ChaosProperties.class) //This is the exact line that officially turns ChaosProperties record on
// and tells Spring Boot to start reading the chaos: block from your application.yml
public class ChaosWebConfig implements WebMvcConfigurer{
    //By implementing WebMvcConfigurer, you are unlocking Spring Boot's internal web settings.
    // It gives you the power to override default behaviors, like adding custom interceptors to the HTTP traffic pipeline.

    private final ChaosDecider decider;
    //standard DI so that it can hand over the decider to interceptor
    public ChaosWebConfig(ChaosDecider decider){
        this.decider = decider;
    }

    /**
     * What it does - You are overriding a built-in Spring method.
     * The InterceptorRegistry is essentially a master list of all the "Bouncers" currently working in the application.
     * You create a brand new instance of the ChaosInterceptor (passing in the dice roller it needs) and add it to the master list.
     *
     *  .addPathPatterns-
     *  It tells the server that this specific Bouncer is only allowed to intercept URLs that start with /aws/ or /gcp/.
     *  1. Because it only targets these paths, your admin dashboard at /chaos/rate is completely safe and will never be accidentally faulted.
     *  2. Because you didn't include /actuator/, your server's health checks are safe.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new ChaosInterceptor(decider)).addPathPatterns("/aws/**","/gcp/**");
        // addPathPatterns - limits chaos to the vendor endpoints
        // deliberately NOT /actuator/** — health/info must never be faulted
    }
}

/**
 * @Override (A standard Java feature)
 * This is a safety net provided by the Java language itself. When you implement an interface (like WebMvcConfigurer),
 * you are signing a contract that says "I will provide my own version of the methods defined in this interface."
 * What it does: It tells the Java compiler, "Hey, I am intentionally replacing a default method from the parent interface."
 */

/**
 * @Configuration
 * Spring Boot manages hundreds of classes behind the scenes. It needs to know which ones are standard web traffic handlers, which ones are database tools, and which ones are system settings.
 * What it does: It marks the class as a Setup File. When the application first boots up, Spring aggressively scans your project looking for this exact annotation.
 * Why it's useful: When Spring sees @Configuration, it says, "Pause everything. Let me read this file first and execute its instructions before I open the web server to the public."
 * It ensures your Bouncer (ChaosInterceptor) is fully hired and at the door before any traffic arrives.
 */