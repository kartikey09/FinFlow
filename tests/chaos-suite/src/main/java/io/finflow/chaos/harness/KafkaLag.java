package io.finflow.chaos.harness;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Total un-consumed records per consumer group, straight from the broker.
 *
 * <p>Deliberately NOT scraping kafka-exporter's /metrics (Day 23). The chaos suite
 * must not depend on the monitoring stack being up — a test that fails because
 * Grafana isn't running is a test nobody trusts. AdminClient talks to the broker.
 *
 * <p>Broker-side offsets are also the only lag that still exists after we SIGKILL a
 * consumer, which is precisely the moment we care.
 */
public final class KafkaLag implements AutoCloseable {

    private final Admin admin;

    public KafkaLag(String bootstrapServers) {
        Properties p = new Properties();
        p.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000);
        p.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 30_000);
        this.admin = Admin.create(p);
    }

    /** group -> sum(endOffset - committedOffset) across every partition it owns. */
    public Map<String, Long> byConsumerGroup() {
        Map<String, Long> out = new LinkedHashMap<>();
        try {
            var groups = admin.listConsumerGroups().all().get().stream()
                    .map(g -> g.groupId())
                    .filter(g -> !g.startsWith("connect-"))   // Debezium's own internals; not ours
                    .collect(Collectors.toList());

            for (String group : groups) {
                Map<TopicPartition, OffsetAndMetadata> committed =
                        admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
                if (committed.isEmpty()) {
                    out.put(group, 0L);
                    continue;
                }
                Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
                committed.keySet().forEach(tp -> specs.put(tp, OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                        admin.listOffsets(specs).all().get();

                long lag = 0;
                for (var e : committed.entrySet()) {
                    var end = ends.get(e.getKey());
                    if (end == null || e.getValue() == null) continue;
                    lag += Math.max(0, end.offset() - e.getValue().offset());
                }
                out.put(group, lag);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read consumer-group lag from Kafka", e);
        }
        return out;
    }

    @Override
    public void close() {
        admin.close();
    }
}
