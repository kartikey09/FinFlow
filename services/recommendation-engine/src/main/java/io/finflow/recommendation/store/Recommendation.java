package io.finflow.recommendation.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One actionable recommendation. Keyed in the DB by (tenant, type, commitment) so a
 * repeated signal for the same commitment UPDATES this row rather than piling up a
 * new one — see V1__recommendations.sql for the reasoning.
 *
 * <p>evidence is stored as JSONB via the same {@code @JdbcTypeCode(SqlTypes.JSON)}
 * trick the outbox uses: the field is a JSON String, Hibernate handles the binary
 * conversion. Keeping it as a String (not a Map) means the exact JSON the rule
 * produced is what gets stored and what gets published — no re-serialization drift.
 */
@Entity
@Table(name = "recommendation")
public class Recommendation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "commitment_id", length = 128)
    private String commitmentId;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "vendor", length = 16)
    private String vendor;

    @Column(name = "service", length = 128)
    private String service;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "estimated_savings_usd", nullable = false)
    private double estimatedSavingsUsd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    private String evidence;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "OPEN";

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Recommendation() { /* for JPA */ }

    public Recommendation(UUID id, String tenantId, String type) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
    }

    /** Apply (or re-apply) a rule's finding to this row. */
    public void apply(String commitmentId, String accountId, String vendor, String service,
                      String region, double estimatedSavingsUsd, String evidence, UUID eventId) {
        this.commitmentId = commitmentId;
        this.accountId = accountId;
        this.vendor = vendor;
        this.service = service;
        this.region = region;
        this.estimatedSavingsUsd = estimatedSavingsUsd;
        this.evidence = evidence;
        this.lastEventId = eventId;
        this.status = "OPEN";
        this.updatedAt = OffsetDateTime.now();
    }

    public void supersede() {
        this.status = "SUPERSEDED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID    getId()                  { return id; }
    public String  getTenantId()            { return tenantId; }
    public String  getType()                { return type; }
    public String  getCommitmentId()        { return commitmentId; }
    public String  getAccountId()           { return accountId; }
    public String  getVendor()              { return vendor; }
    public String  getService()             { return service; }
    public String  getRegion()              { return region; }
    public double  getEstimatedSavingsUsd() { return estimatedSavingsUsd; }
    public String  getEvidence()            { return evidence; }
    public String  getStatus()              { return status; }
    public UUID    getLastEventId()         { return lastEventId; }
    public OffsetDateTime getUpdatedAt()    { return updatedAt; }
}
