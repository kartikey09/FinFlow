package io.finflow.ingestion.gcp.ingest;

import io.finflow.ingestion.gcp.client.GcpBillingRow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A row in {@code gcp_ingestion.gcp_cost_line_items_raw} — the ingestor's durable
 * copy of one GCP billing-export row, and the idempotency gate ({@code row_key}
 * is the PK).
 *
 * <p>This is where GCP's derived values are persisted alongside the raw ones:
 * {@code cost_usd} (after currency conversion) and {@code committed_usage_discount}
 * (summed from the credits array). Storing both the original {@code cost}/{@code
 * currency} and the derived USD makes the conversion auditable later.
 */
@Entity
@Table(name = "gcp_cost_line_items_raw", schema = "gcp_ingestion")
public class GcpCostLineItemRaw {

    @Id
    @Column(name = "row_key", length = 64)
    private String rowKey;

    @Column(name = "billing_account_id", length = 64)
    private String billingAccountId;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Column(name = "service_description", length = 255)
    private String serviceDescription;

    @Column(name = "sku_id", length = 64)
    private String skuId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "usage_start_time", length = 64)
    private String usageStartTime;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "currency_conversion_rate")
    private Double currencyConversionRate;

    @Column(name = "cost_usd")
    private Double costUsd;

    @Column(name = "committed_usage_discount")
    private Double committedUsageDiscount;

    @Column(name = "cost_type", length = 64)
    private String costType;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt = OffsetDateTime.now();

    protected GcpCostLineItemRaw() { /* for JPA */ }

    private GcpCostLineItemRaw(GcpBillingRow row) {
        this.rowKey = row.rowKey();
        this.billingAccountId = row.billingAccountId();
        this.serviceId = row.service() != null ? row.service().id() : null;
        this.serviceDescription = row.service() != null ? row.service().description() : null;
        this.skuId = row.sku() != null ? row.sku().id() : null;
        this.projectId = row.project() != null ? row.project().id() : null;
        this.usageStartTime = row.usageStartTime();
        this.cost = row.cost();
        this.currency = row.currency();
        this.currencyConversionRate = row.currencyConversionRate();
        this.costUsd = row.costInUsd();                       // GCP-specific: FX conversion
        this.committedUsageDiscount = row.committedUsageDiscount();  // GCP-specific: credit walk
        this.costType = row.costType();
    }

    /** Project a fetched GCP row (with its derived USD + CUD) into a raw-landing row. */
    public static GcpCostLineItemRaw from(GcpBillingRow row) {
        return new GcpCostLineItemRaw(row);
    }

    public String getRowKey()                 { return rowKey; }
    public String getBillingAccountId()       { return billingAccountId; }
    public String getServiceId()              { return serviceId; }
    public String getSkuId()                  { return skuId; }
    public String getProjectId()              { return projectId; }
    public Double getCost()                   { return cost; }
    public String getCurrency()               { return currency; }
    public Double getCostUsd()                { return costUsd; }
    public Double getCommittedUsageDiscount() { return committedUsageDiscount; }
    public OffsetDateTime getIngestedAt()     { return ingestedAt; }
}
