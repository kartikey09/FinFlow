package io.finflow.query.projection.commitment;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link CommitmentHealth}: one row per (tenant, commitment). */
public class CommitmentHealthKey implements Serializable {
    private String tenantId;
    private String commitmentId;

    public CommitmentHealthKey() { /* for JPA */ }

    public CommitmentHealthKey(String tenantId, String commitmentId) {
        this.tenantId = tenantId; this.commitmentId = commitmentId;
    }

    public String getTenantId()      { return tenantId; }
    public String getCommitmentId()  { return commitmentId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommitmentHealthKey k)) return false;
        return Objects.equals(tenantId, k.tenantId)
            && Objects.equals(commitmentId, k.commitmentId);
    }
    @Override public int hashCode() { return Objects.hash(tenantId, commitmentId); }
}
