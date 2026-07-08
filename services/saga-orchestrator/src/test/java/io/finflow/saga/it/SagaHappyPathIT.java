package io.finflow.saga.it;

import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.orchestration.SagaOrchestrationService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SagaHappyPathIT (Testcontainers).
 *
 * <p>End-to-end: real Postgres, real Kafka, real orchestrator. This test's
 * setup assumes AWS + GCP adapter workers are ALSO running (they can be
 * launched as ApplicationRunners in the same test JVM, or as sibling
 * Spring Boot processes for a truer integration).
 *
 * <p>Chaos is OFF for the duration. The saga is expected to reach COMPLETED
 * within a reasonable timeout.
 *
 * <p><b>Docker requirement:</b> this test CANNOT run without a working Docker
 * daemon on the host. Until the MacBook Air's Docker runtime issue is
 * resolved, use SagaHappyPathTest (the no-Docker mockito equivalent) for
 * local verification.
 */
@SpringBootTest
@ContextConfiguration(initializers = SagaITBase.TestPropertyInitializer.class)
class SagaHappyPathIT extends SagaITBase {

    @Autowired SagaOrchestrationService orchestration;
    @Autowired SagaInstanceRepository repo;

    @Test
    void awsSagaCompletes() {
        var saga = orchestration.startRebalance("happy-it-" + System.nanoTime(), Vendor.AWS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var reloaded = repo.findById(saga.getId()).orElseThrow();
                    assertThat(reloaded.getCurrentState()).isEqualTo(SagaState.COMPLETED);
                });
    }
}
