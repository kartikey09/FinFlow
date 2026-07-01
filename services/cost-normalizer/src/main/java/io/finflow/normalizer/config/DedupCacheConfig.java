package io.finflow.normalizer.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * The L1 dedup cache — last N (default 100k) event ids seen, in-memory, in front
 * of the DB-backed processed_event ledger (L2a) and the UNIQUE constraint on
 * cost_ledger (L2b).
 *
 * <p>Why three layers? The cache is a hot-path optimization: redeliveries
 * usually arrive within seconds of the original, so most duplicates can be
 * recognized without a DB round-trip. But Caffeine has no durability and bounded
 * capacity — a restart empties it and the LRU eviction drops old ids — so we
 * still need the durable ledger underneath. The UNIQUE on cost_ledger is the
 * belt-AND-suspenders bottom: even if both caches miss (impossibly bad luck),
 * the database refuses a second row for the same (tenant_id, event_id).
 */
@Configuration
public class DedupCacheConfig {

    @Bean
    public Cache<UUID, Boolean> processedEventCache(
            @Value("${finflow.dedup.cache-size:100000}") long maximumSize) {
        // Boolean.TRUE is just a presence marker — only the key matters.
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .recordStats()
                .build();
    }
}
