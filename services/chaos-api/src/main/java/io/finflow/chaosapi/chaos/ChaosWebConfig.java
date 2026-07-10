package io.finflow.chaosapi.chaos;

//EnableConfigurationProperties - Activates ChaosProperties binding from application.yml
//without this it wouldn't know how to link chaosProperties to application.yml file
// activates that YAML-to-Java translation
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * <p>Day 22: the interceptor now also needs a MeterRegistry (to emit
 * finflow.chaos.faults.injected), so we inject it here and pass it through.
 * It's injected via {@link ObjectProvider} and resolved with
 * {@code getIfAvailable()} so the config still loads when no registry is on the
 * context (e.g. a {@code @WebMvcTest} slice) — the interceptor null-checks the
 * registry and simply skips the metric. In production every FinFlow service has
 * the Prometheus registry, so the counter is always live.
 */
@Configuration
@EnableConfigurationProperties(ChaosProperties.class) //This is the exact line that officially turns ChaosProperties record on
// and tells Spring Boot to start reading the chaos: block from your application.yml
public class ChaosWebConfig implements WebMvcConfigurer{
    //By implementing WebMvcConfigurer, you are unlocking Spring Boot's internal web settings.
    // It gives you the power to override default behaviors, like adding custom interceptors to the HTTP traffic pipeline.

    private final ChaosDecider decider;
    private final MeterRegistry meterRegistry;
    //standard DI so that it can hand over the decider (and the registry) to the interceptor.
    //MeterRegistry is optional (getIfAvailable() -> null when absent) so this config
    //still loads in a @WebMvcTest slice that doesn't auto-configure metrics.
    public ChaosWebConfig(ChaosDecider decider, ObjectProvider<MeterRegistry> meterRegistry){
        this.decider = decider;
        this.meterRegistry = meterRegistry.getIfAvailable();
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
        registry.addInterceptor(new ChaosInterceptor(decider, meterRegistry)).addPathPatterns("/aws/**","/gcp/**");
        // addPathPatterns - limits chaos to the vendor endpoints
        // deliberately NOT /actuator/** — health/info must never be faulted
    }
}
