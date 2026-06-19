package io.finflow.ingestion.aws.ingest;

import io.finflow.ingestion.aws.client.CurLineItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/* ============================================================================
 * ARCHITECTURAL OVERVIEW: THE RAW LANDING ZONE (DATABASE ENTITY)
 * ============================================================================
 * What this module does:
 * This class is the exact blueprint for the `cost_line_items_raw` table in
 * PostgreSQL. While `CurLineItem` (the Java Record we looked at earlier) represents
 * data traveling over the network, THIS class represents data sitting safely
 * on your hard drive.
 *
 * Why we built it this way (The Interview Flex):
 * 1. The Idempotency Anchor: The Primary Key (@Id) is the `lineItemId` provided
 * by AWS. If our network loop glitches and tries to save the same AWS row twice,
 * the database will physically block it because Primary Keys must be unique.
 * 2. Immutable Financial Data: Notice there are NO "Setter" methods
 * (e.g., setUnblendedCost). Once a billing row is written to this table, it
 * is mathematically impossible for another part of our Java code to accidentally
 * change the price. It is an "append-only" ledger.
 * 3. The JPA "Record" Limitation: You might wonder, "Why isn't this a Java
 * Record like the network object?" Because Hibernate (the tool that writes our
 * SQL) requires a blank, no-argument constructor to build objects using reflection.
 * Java Records don't allow that, so we must use a standard Class for entities.
 * ============================================================================
 */

@Entity
@Table(name = "cost_line_items_raw", schema = "ingestion")
public class CostLineItemRaw {

    // We use the exact, unique string ID that AWS generated for this specific
    // fraction of a cent.
    @Id
    @Column(name="line_item_id", length = 128)
    private String lineItemId;

    // These map directly to the database columns. We define lengths to prevent
    // malicious or malformed data from blowing up our database storage.
    @Column(name = "payer_account_id", length = 32)
    private String payerAccountId;

    @Column(name = "usage_account_id", length = 32)
    private String usageAccountId;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "usage_type", length = 128)
    private String usageType;

    @Column(name = "unblended_cost")
    private Double unblendedCost;

    @Column(name = "usage_start_date", length = 64)
    private String usageStartDate;

    @Column(name = "usage_end_date", length = 64)
    private String usageEndDate;

    // We capture the exact millisecond this row landed in our database. AWS
    // doesn't provide this; we generate it locally. It is strictly for debugging
    // so we can see how fast our pipeline is running.
    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt = OffsetDateTime.now();

    // Hibernate REQUIRES an empty constructor to work its magic. We make it
    // 'protected' so other developers can't accidentally use it to create empty,
    // invalid billing rows in the code.
    protected CostLineItemRaw() {
        // for Jpa
    }

    // We only allow the creation of this entity by passing in the network DTO
    // (the CurLineItem Record). This constructor copies the data from the network
    // object into the database object.
    private CostLineItemRaw(CurLineItem item){
        this.lineItemId = item.lineItemId();
        this.payerAccountId = item.payerAccountId();
        this.usageAccountId = item.usageAccountId();
        this.productCode = item.productCode();
        this.usageType = item.usageType();
        this.unblendedCost = item.unblendedCost();
        this.usageStartDate = item.usageStartDate();
        this.usageEndDate = item.usageEndDate();
    }

    public static CostLineItemRaw from(CurLineItem item){
        return new CostLineItemRaw(item);
    }

    // only getters and no setters to guarantee immutability
    public String getLineItemId()       { return lineItemId; }
    public String getPayerAccountId()   { return payerAccountId; }
    public String getUsageAccountId()   { return usageAccountId; }
    public String getProductCode()      { return productCode; }
    public String getUsageType()        { return usageType; }
    public Double getUnblendedCost()    { return unblendedCost; }
    public String getUsageStartDate()   { return usageStartDate; }
    public String getUsageEndDate()     { return usageEndDate; }
    public OffsetDateTime getIngestedAt() { return ingestedAt; }
}
