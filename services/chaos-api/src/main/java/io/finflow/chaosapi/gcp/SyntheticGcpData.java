package io.finflow.chaosapi.gcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.chaosapi.gcp.dto.GcpBillingRow;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Loads the synthetic GCP billing rows from a classpath JSON resource once at
 * startup and holds them in memory. The GCP-side mirror of SyntheticCurData.
 *
 * Same pattern as the AWS loader by design: constructor-inject the configured
 * ObjectMapper, read the fixture once in a package-private @PostConstruct (so
 * tests can call load() without a Spring context), expose an immutable list.
 */
@Component
public class SyntheticGcpData {

    private static final Logger log = LoggerFactory.getLogger(SyntheticGcpData.class);
    private static final String RESOURCE = "data/gcp-billing-rows.json";

    private final ObjectMapper objectMapper;
    private List<GcpBillingRow> rows = List.of();

    public SyntheticGcpData(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws Exception {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            rows = List.of(objectMapper.readValue(in, GcpBillingRow[].class));
        }
        log.info("[GCP-CHAOS] loaded {} synthetic billing rows from {}",
                rows.size(), RESOURCE);
    }

    public List<GcpBillingRow> all() {
        return rows;
    }
}
