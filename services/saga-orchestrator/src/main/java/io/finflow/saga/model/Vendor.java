package io.finflow.saga.model;

/**
 * Which vendor's adapter handles a saga's commands.
 *
 * <p>Stored on the {@link SagaInstance} row. Read by the emitter to pick the
 * outbound topic. Persisted as a string via {@code @Enumerated(EnumType.STRING)}
 * so renaming here would fail loudly at boot rather than silently corrupt data.
 */
public enum Vendor {
    AWS, GCP;

    public static Vendor requireOf(String raw) {
        if (raw == null) throw new IllegalArgumentException("vendor is required");
        try { return Vendor.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown vendor: " + raw + " (expected AWS or GCP)");
        }
    }
}
