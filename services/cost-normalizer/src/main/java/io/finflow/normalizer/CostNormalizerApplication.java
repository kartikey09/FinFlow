package io.finflow.normalizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * cost-normalizer — consumes billing events from the CDC pipeline.
 *
 * Day 9 scope: the consumer plumbing only (subscribe, deserialize, dedupe).
 * Day 13 adds the actual cost-normalization logic on top of this consumer.
 */

@SpringBootApplication
public class CostNormalizerApplication{
    public static void main(String[] args){
        SpringApplication.run(CostNormalizerApplication.class, args);
    }
}