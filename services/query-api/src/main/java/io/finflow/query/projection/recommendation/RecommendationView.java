package io.finflow.query.projection.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read-model row for a recommendation (Day 26), maintained from finflow.events.recommendation. */
@Entity
@Table(name = "mv_recommendation")
public class RecommendationView {

    @Id
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)  private String tenantId;
    @Column(name = "type", length = 32, nullable = false)       private String type;
    @Column(name = "commitment_id", length = 128)              private String commitmentId;
    @Column(name = "account_id", length = 64)                  private String accountId;
    @Column(name = "vendor", length = 16)                      private String vendor;
    @Column(name = "service", length = 128)                    private String service;
    @Column(name = "region", length = 64)                      private String region;
    @Column(name = "estimated_savings_usd", nullable = false)  private double estimatedSavingsUsd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")     private String evidence;

    @Column(name = "updated_at", nullable = false)             private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected RecommendationView() { /* for JPA */ }

    public RecommendationView(UUID id) { this.id = id; }

    public void update(String tenantId, String type, String commitmentId, String accountId,
                       String vendor, String service, String region,
                       double estimatedSavingsUsd, String evidence) {
        this.tenantId = tenantId;
        this.type = type;
        this.commitmentId = commitmentId;
        this.accountId = accountId;
        this.vendor = vendor;
        this.service = service;
        this.region = region;
        this.estimatedSavingsUsd = estimatedSavingsUsd;
        this.evidence = evidence;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID    getId()                  { return id; }
    public String  getType()                { return type; }
    public String  getCommitmentId()        { return commitmentId; }
    public String  getAccountId()           { return accountId; }
    public String  getVendor()              { return vendor; }
    public String  getService()             { return service; }
    public String  getRegion()              { return region; }
    public double  getEstimatedSavingsUsd() { return estimatedSavingsUsd; }
    public String  getEvidence()            { return evidence; }
}
