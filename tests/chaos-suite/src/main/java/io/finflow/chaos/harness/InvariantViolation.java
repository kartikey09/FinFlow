package io.finflow.chaos.harness;

/**
 * One thing that went wrong. Collected rather than thrown, so a single run reports
 * EVERY violation instead of dying on the first — when 20 sagas misbehave you want
 * the whole picture, not just saga #3.
 */
public record InvariantViolation(String invariant, String detail) {

    @Override
    public String toString() {
        return "[" + invariant + "] " + detail;
    }
}
