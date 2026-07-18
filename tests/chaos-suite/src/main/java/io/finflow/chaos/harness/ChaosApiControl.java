package io.finflow.chaos.harness;

/**
 * Drives the Chaos API's own fault injection (Week 1) via its /chaos control plane.
 *
 * <p>This is APPLICATION-layer chaos: the fake vendor returns 503s and 5-second
 * hangs. It exercises Resilience4j — Retry, CircuitBreaker, TimeLimiter, Bulkhead.
 * Toxiproxy (see {@link ToxiproxyControl}) is TRANSPORT-layer chaos and exercises
 * something completely different: Kafka redelivery, the outbox, and idempotency.
 * Both are needed; they break different things.
 */
public final class ChaosApiControl {

    private final String baseUrl;

    public ChaosApiControl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** @param percent 0-100. The plan's headline scenario runs at 30. */
    public void faultRate(int percent) {
        Http.post(baseUrl + "/chaos/fault-rate?value=" + percent, null);
    }

    public void enabled(boolean on) {
        Http.post(baseUrl + "/chaos/enabled?value=" + on, null);
    }

    public String status() {
        return Http.get(baseUrl + "/chaos/status").body();
    }

    /** Put it back the way we found it. Always call this in a finally / @AfterEach. */
    public void reset() {
        faultRate(0);
        enabled(false);
    }
}
