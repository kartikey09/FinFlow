package io.finflow.query.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Small but load-bearing — this parser controls every spend query's date window. */
class PeriodParserTest {

    /** Freeze the clock on 2025-11-15 so windows are deterministic. */
    private final Clock fixed = Clock.fixed(
            LocalDate.of(2025, 11, 15).atStartOfDay().toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);
    private final PeriodParser parser = new PeriodParser(fixed);

    @Test
    void lastThirtyDaysIsInclusiveOfToday() {
        PeriodParser.Window w = parser.parse("last-30d");
        assertThat(w.to()).isEqualTo(LocalDate.of(2025, 11, 15));
        assertThat(w.from()).isEqualTo(LocalDate.of(2025, 10, 17));    // 30 days inclusive
    }

    @Test
    void lastSevenDaysWorksToo() {
        PeriodParser.Window w = parser.parse("last-7d");
        assertThat(w.from()).isEqualTo(LocalDate.of(2025, 11,  9));
        assertThat(w.to())  .isEqualTo(LocalDate.of(2025, 11, 15));
    }

    @Test
    void nullOrGarbageFallsBackToLastThirtyDays() {
        assertThat(parser.parse(null).from()).isEqualTo(LocalDate.of(2025, 10, 17));
        assertThat(parser.parse("bogus").from()).isEqualTo(LocalDate.of(2025, 10, 17));
        assertThat(parser.parse("last-abcd").from()).isEqualTo(LocalDate.of(2025, 10, 17));
    }
}
