package io.finflow.query.projection.spend;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The addSpend() fold is called for every event; getting it wrong quietly
 * corrupts the dashboard. This tests it in isolation, no DB, no Spring.
 */
class SpendByTeamDayTest {

    @Test
    void addSpendAccumulatesCostAndIncrementsCount() {
        SpendByTeamDay row = new SpendByTeamDay("default", "platform", LocalDate.of(2025, 11, 1));

        row.addSpend(10.0, UUID.randomUUID());
        row.addSpend(25.5, UUID.randomUUID());

        assertThat(row.getCostUsd()).isEqualTo(35.5);
        assertThat(row.getEventCount()).isEqualTo(2);
    }

    @Test
    void lastEventIdIsUpdatedOnEachFold() {
        SpendByTeamDay row = new SpendByTeamDay("default", "data", LocalDate.of(2025, 11, 1));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        row.addSpend(1.0, a);
        assertThat(row.getLastEventId()).isEqualTo(a);
        row.addSpend(1.0, b);
        assertThat(row.getLastEventId()).isEqualTo(b);
    }
}
