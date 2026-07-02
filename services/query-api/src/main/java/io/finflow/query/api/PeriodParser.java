package io.finflow.query.api;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Turns the {@code period} query parameter into a concrete date window.
 *
 * <p>Grammar supported today: {@code last-Nd} (e.g. {@code last-30d}) or the
 * default. The plan example is {@code last-30d}. Kept as its own class so it's
 * testable without booting Spring, and so extending later ({@code this-month},
 * {@code YYYY-MM}) is a single place.
 */
public class PeriodParser {

    public record Window(LocalDate from, LocalDate to) {}

    private final Clock clock;

    public PeriodParser() { this(Clock.systemUTC()); }
    public PeriodParser(Clock clock) { this.clock = clock; }

    public Window parse(String period) {
        LocalDate today = LocalDate.now(clock);
        int days = 30;                                   // default
        if (period != null && period.startsWith("last-") && period.endsWith("d")) {
            String number = period.substring(5, period.length() - 1);
            try { days = Integer.parseInt(number); }
            catch (NumberFormatException e) { /* fall through to default */ }
        }
        return new Window(today.minusDays(days - 1L), today);
    }
}
