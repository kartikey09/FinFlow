package io.finflow.chaos.harness;

/**
 * Breaks the Kafka TCP connection on purpose, via Toxiproxy's admin API (:8474).
 *
 * <p>The "kafka" proxy itself is pre-provisioned in infra/docker/toxiproxy/toxiproxy.json
 * (listen 0.0.0.0:26092 -> upstream kafka:29192), so this class only adds and removes
 * TOXICS at runtime.
 *
 * <h2>Honest note: Toxiproxy has no "packet loss" toxic</h2>
 *
 * <p>The build plan asks for "50% packet loss on Kafka traffic". Toxiproxy cannot do
 * that — it is a TCP-STREAM proxy, not a packet-level one. There is no toxic that
 * drops individual IP packets. The real toxic list is: latency, down, bandwidth,
 * slow_close, timeout, slicer, limit_data, reset_peer.
 *
 * <p>What Toxiproxy DOES have is {@code toxicity}: the probability (0.0-1.0) that a
 * given connection gets the toxic at all. So the faithful translation of "50% packet
 * loss" is {@code reset_peer} at {@code toxicity: 0.5} — half of all Kafka connections
 * are abruptly TCP-RST mid-stream.
 *
 * <p>That is arguably a HARSHER test than packet loss, and a more useful one. Dropped
 * packets are mostly absorbed by TCP retransmission and the app never notices. A
 * connection reset cannot be absorbed: the producer's in-flight batch dies, the
 * consumer's session drops, the group rebalances, and Kafka redelivers. Redelivery is
 * exactly the condition under which idempotency either holds or doesn't — which is the
 * thing this scenario exists to prove.
 */
public final class ToxiproxyControl {

    public static final String PROXY = "kafka";
    private static final String RESET_TOXIC = "kafka_reset_peer";
    private static final String LATENCY_TOXIC = "kafka_latency";

    private final String adminUrl;

    public ToxiproxyControl(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    /**
     * Reset {@code toxicity} of all Kafka connections, mid-stream.
     *
     * @param toxicity 0.0-1.0. The plan's "50% packet loss" maps to 0.5.
     */
    public void resetPeer(double toxicity) {
        Http.post(adminUrl + "/proxies/" + PROXY + "/toxics", """
                {
                  "name": "%s",
                  "type": "reset_peer",
                  "stream": "downstream",
                  "toxicity": %s,
                  "attributes": { "timeout": 0 }
                }
                """.formatted(RESET_TOXIC, toxicity));
    }

    /** Optional milder toxic: adds delay + jitter rather than killing the connection. */
    public void latency(long millis, long jitterMillis, double toxicity) {
        Http.post(adminUrl + "/proxies/" + PROXY + "/toxics", """
                {
                  "name": "%s",
                  "type": "latency",
                  "stream": "downstream",
                  "toxicity": %s,
                  "attributes": { "latency": %d, "jitter": %d }
                }
                """.formatted(LATENCY_TOXIC, toxicity, millis, jitterMillis));
    }

    /** Remove every toxic. Safe to call when none exist (404s are ignored). */
    public void healAll() {
        removeQuietly(RESET_TOXIC);
        removeQuietly(LATENCY_TOXIC);
    }

    private void removeQuietly(String toxic) {
        try {
            Http.delete(adminUrl + "/proxies/" + PROXY + "/toxics/" + toxic);
        } catch (RuntimeException ignored) {
            // Toxic wasn't there. Fine — healAll() must be idempotent.
        }
    }

    public boolean proxyExists() {
        try {
            return Http.get(adminUrl + "/proxies/" + PROXY).statusCode() == 200;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
