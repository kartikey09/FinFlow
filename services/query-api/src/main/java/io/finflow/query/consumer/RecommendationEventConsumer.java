package io.finflow.query.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.query.projection.recommendation.RecommendationView;
import io.finflow.query.projection.recommendation.RecommendationViewRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Day 26: projects finflow.events.recommendation into read_model.mv_recommendation so
 * the dashboard's "top recommendations" panel and GET /api/v1/recommendations have a
 * source. Idempotent by primary key — the recommendation's own id — so a redelivered
 * event just re-UPSERTs the same row.
 *
 * <p>Follows the exact shape of the existing commitment/cost consumers: parse the JSON
 * payload, upsert, manual-ack, swallow poison records.
 */
@Component
public class RecommendationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEventConsumer.class);

    private final RecommendationViewRepository repository;
    private final ObjectMapper objectMapper;

    public RecommendationEventConsumer(RecommendationViewRepository repository,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "finflow.events.recommendation")
    @Transactional
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            RecoPayload p = objectMapper.readValue(record.value(), RecoPayload.class);
            if (p.id() == null) {
                ack.acknowledge();
                return;
            }
            RecommendationView row = repository.findById(p.id())
                    .orElseGet(() -> new RecommendationView(p.id()));
            row.update(p.tenantId(), p.type(), p.commitmentId(), p.accountId(),
                    p.vendor(), p.service(), p.region(),
                    p.estimatedSavingsUsd() == null ? 0.0 : p.estimatedSavingsUsd(),
                    p.evidenceJson());
            repository.save(row);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to project recommendation record (offset={}): {}", record.offset(), e.toString());
            ack.acknowledge();
        }
    }

    /** Mirror of RecommendationCreated; lenient so added fields don't break parsing. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecoPayload(
            UUID id,
            String tenantId,
            String type,
            String commitmentId,
            String accountId,
            String vendor,
            String service,
            String region,
            Double estimatedSavingsUsd,
            String evidenceJson
    ) {}
}
