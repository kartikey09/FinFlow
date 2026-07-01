package io.finflow.normalizer.normalize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in {@code normalizer.cost_ledger} — the persistent form of one
 * {@link CostLineItemNormalized}. Insert-only.
 *
 * <p>The {@code UNIQUE(tenant_id, event_id)} constraint here is the
 * <b>L2 dedup gate</b>: if a duplicate gets past the in-memory cache and the
 * processed_event ledger (impossibly bad luck — but possible), the database
 * still refuses a second row. The consumer catches the resulting
 * {@code DataIntegrityViolationException}, logs it, and drops the duplicate
 * without failing.
 *
 * <p>Schema-unqualified, so it follows {@code hibernate.default_schema=normalizer}.
 * (Unlike OutboxEvent, which is pinned to {@code public} via {@code orm.xml}.)
 */
@Entity
@Table(name = "cost_ledger",
       uniqueConstraints = @UniqueConstraint(name = "uq_cost_ledger_tenant_event",
                                             columnNames = {"tenant_id", "event_id"}))
public class CostLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "vendor", length = 16, nullable = false)
    private String vendor;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "team", length = 64, nullable = false)
    private String team;

    @Column(name = "service", length = 128)
    private String service;

    @Column(name = "resource_id", length = 256)
    private String resourceId;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "usage_start", length = 64)
    private String usageStart;

    @Column(name = "usage_end", length = 64)
    private String usageEnd;

    @Column(name = "usage_amount")
    private Double usageAmount;

    @Column(name = "cost_usd", nullable = false)
    private Double costUsd;

    @Column(name = "list_price_usd")
    private Double listPriceUsd;

    @Column(name = "commitment_id", length = 128)
    private String commitmentId;

    @Column(name = "line_item_type", length = 64)
    private String lineItemType;

    @Column(name = "raw_source", length = 16, nullable = false)
    private String rawSource;

    @Column(name = "normalized_at", nullable = false)
    private OffsetDateTime normalizedAt = OffsetDateTime.now();

    protected CostLedgerEntry() { /* for JPA */ }

    private CostLedgerEntry(CostLineItemNormalized n) {
        this.tenantId      = n.tenantId();
        this.eventId       = n.eventId();
        this.vendor        = n.vendor();
        this.accountId     = n.accountId();
        this.team          = n.team();
        this.service       = n.service();
        this.resourceId    = n.resourceId();
        this.region        = n.region();
        this.usageStart    = n.usageStart();
        this.usageEnd      = n.usageEnd();
        this.usageAmount   = n.usageAmount();
        this.costUsd       = n.costUsd();
        this.listPriceUsd  = n.listPriceUsd();
        this.commitmentId  = n.commitmentId();
        this.lineItemType  = n.lineItemType();
        this.rawSource     = n.rawSource();
    }

    public static CostLedgerEntry from(CostLineItemNormalized n) {
        return new CostLedgerEntry(n);
    }

    public Long getId()                  { return id; }
    public UUID getEventId()             { return eventId; }
    public String getTenantId()          { return tenantId; }
    public Double getCostUsd()           { return costUsd; }
    public String getTeam()              { return team; }
    public String getVendor()            { return vendor; }
    public String getCommitmentId()      { return commitmentId; }
    public OffsetDateTime getNormalizedAt() { return normalizedAt; }
}
