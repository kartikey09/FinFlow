package io.finflow.commitment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A reservation / savings plan / committed-use discount — the system's master
 * record of "this commitment exists, here is its daily ceiling and when it
 * expires."
 *
 * <p>Read-only at runtime today (seeded by the V1 migration); the production
 * version would reconcile against the vendor APIs on a schedule. The tracker
 * joins this in to know each event's {@code daily_amount_usd} (the available
 * ceiling) and {@code expires_at} (for the expiring-soon rule).
 */
@Entity
@Table(name = "commitments")
public class Commitment {

    @Id
    @Column(name = "commitment_id", length = 128)
    private String commitmentId;

    @Column(name = "vendor", length = 16, nullable = false)
    private String vendor;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "service", length = 128)
    private String service;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "daily_amount_usd", nullable = false)
    private Double dailyAmountUsd;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected Commitment() { /* for JPA */ }

    public String getCommitmentId()       { return commitmentId; }
    public String getVendor()             { return vendor; }
    public String getType()               { return type; }
    public String getAccountId()          { return accountId; }
    public String getService()            { return service; }
    public Double getDailyAmountUsd()     { return dailyAmountUsd; }
    public OffsetDateTime getStartAt()    { return startAt; }
    public OffsetDateTime getExpiresAt()  { return expiresAt; }
}
