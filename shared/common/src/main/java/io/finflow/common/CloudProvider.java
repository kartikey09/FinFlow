package io.finflow.common;

/**
 * The cloud vendors FinFlow reconciles. The first piece of the canonical domain:
 * once the cost-normalizer (Week 3) collapses AWS's flat CUR and GCP's nested
 * export into one shape, every canonical event carries one of these.
 */
public enum CloudProvider {
    AWS("Amazon Web Services"),
    GCP("Google Cloud Platform");

    private final String displayName;

    CloudProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
