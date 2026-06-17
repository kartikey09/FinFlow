package io.finflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final OutboxEventRepository repository;
    private final Duration retention;

    public OutboxRetentionJob(OutboxEventRepository repository, Duration retention){
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(cron = "${finflow.outbox.retention.cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredEvents(){

    }
}
