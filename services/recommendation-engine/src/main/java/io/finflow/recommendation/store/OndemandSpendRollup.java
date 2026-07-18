package io.finflow.recommendation.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Engine-local running tally of on-demand (uncommitted) spend per (account, service).
 * Accumulated from the cost stream because the engine can't read cost-normalizer's DB
 * — schema-per-service. Drives the PURCHASE_COMMITMENT rule, which needs a TREND
 * ("sustained spend"), not a single line item.
 */
@Entity
@Table(name = "ondemand_spend_rollup")
@IdClass(OndemandSpendRollupKey.class)
public class OndemandSpendRollup {

    @Id @Column(name = "tenant_id",  length = 64)  private String tenantId;
    @Id @Column(name = "account_id", length = 64)  private String accountId;
    @Id @Column(name = "service",    length = 128) private String service;

    @Column(name = "vendor", length = 16) private String vendor;
    @Column(name = "region", length = 64) private String region;

    @Column(name = "observed_days", nullable = false)      private int observedDays = 0;
    @Column(name = "total_ondemand_usd", nullable = false) private double totalOndemandUsd = 0.0;
    @Column(name = "last_usage_day", length = 64)          private String lastUsageDay;
    @Column(name = "last_event_id")                        private UUID lastEventId;
    @Column(name = "updated_at", nullable = false)         private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected OndemandSpendRollup() { /* for JPA */ }

    public OndemandSpendRollup(String tenantId, String accountId, String service) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.service = service;
    }

    /**
     * Fold one on-demand cost line into the rollup. observed_days increments only
     * when the usage-day changes, so it approximates "distinct days of sustained
     * spend" rather than "number of line items".
     */
    public void addOndemandCost(String vendor, String region, Double costUsd,
                                String usageDay, UUID eventId) {
        if (vendor != null) this.vendor = vendor;
        if (region != null) this.region = region;
        if (costUsd != null) this.totalOndemandUsd += costUsd;
        if (usageDay != null && !usageDay.equals(this.lastUsageDay)) {
            this.observedDays += 1;
            this.lastUsageDay = usageDay;
        }
        this.lastEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    public String  getTenantId()         { return tenantId; }
    public String  getAccountId()        { return accountId; }
    public String  getService()          { return service; }
    public String  getVendor()           { return vendor; }
    public String  getRegion()           { return region; }
    public int     getObservedDays()     { return observedDays; }
    public double  getTotalOndemandUsd() { return totalOndemandUsd; }
    public UUID    getLastEventId()      { return lastEventId; }
}
