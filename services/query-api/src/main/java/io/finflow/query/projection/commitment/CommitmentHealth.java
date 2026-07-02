package io.finflow.query.projection.commitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The dashboard's per-commitment headline. Maintained by two sources:
 *
 * <ul>
 *   <li><b>Cost-normalized events</b> update the utilization figures
 *       ({@code latest_used_usd} etc.) as spend flows in — same source as
 *       commitment-tracker itself, so this projection stays in step.</li>
 *   <li><b>Commitment alert events</b> flip {@code status} and stamp
 *       {@code last_alert_at} whenever an alert of any kind fires.</li>
 * </ul>
 *
 * <p>No time series: one row per commitment carries the CURRENT picture. That's
 * exactly what a dashboard needs. If a historical trend is ever wanted, add a
 * second projection reading the same events.
 *
 * <p>{@code status} moves via a simple state machine:
 * <pre>
 *   HEALTHY   ─▶ UNDERUTILIZED   (on CommitmentUnderutilized)
 *   HEALTHY   ─▶ SATURATED       (on CommitmentSaturated)
 *   HEALTHY   ─▶ EXPIRING_SOON   (on CommitmentExpiringSoon)
 *   any       ─▶ HEALTHY         (never — status only backfills on next alert)
 * </pre>
 * We deliberately don't auto-heal to HEALTHY: an alert is a fact that happened,
 * so it stays as the last-known state until superseded by another alert.
 */
@Entity
@Table(name = "mv_commitment_health")
@IdClass(CommitmentHealthKey.class)
public class CommitmentHealth {

    @Id @Column(name = "tenant_id",     length = 64)  private String tenantId;
    @Id @Column(name = "commitment_id", length = 128) private String commitmentId;

    @Column(name = "vendor",      length = 16) private String vendor;
    @Column(name = "account_id",  length = 64) private String accountId;

    @Column(name = "latest_period_day")     private LocalDate latestPeriodDay;
    @Column(name = "latest_used_usd")       private Double latestUsedUsd;
    @Column(name = "latest_available_usd")  private Double latestAvailableUsd;
    @Column(name = "latest_fraction")       private Double latestFraction;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "HEALTHY";

    @Column(name = "last_alert_type",   length = 64) private String lastAlertType;
    @Column(name = "last_alert_at")                  private OffsetDateTime lastAlertAt;
    @Column(name = "last_alert_event_id")            private UUID lastAlertEventId;

    @Column(name = "updated_at", nullable = false)   private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CommitmentHealth() { /* for JPA */ }

    public CommitmentHealth(String tenantId, String commitmentId) {
        this.tenantId = tenantId; this.commitmentId = commitmentId;
    }

    public void updateUtilization(String vendor, String accountId, LocalDate day,
                                  Double used, Double available) {
        if (vendor != null) this.vendor = vendor;
        if (accountId != null) this.accountId = accountId;
        this.latestPeriodDay = day;
        this.latestUsedUsd = used;
        this.latestAvailableUsd = available;
        this.latestFraction = (available == null || available == 0.0) ? 0.0 : used / available;
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordAlert(String alertType, String status, UUID eventId) {
        this.lastAlertType = alertType;
        this.status = status;
        this.lastAlertAt = OffsetDateTime.now();
        this.lastAlertEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    public String    getTenantId()          { return tenantId; }
    public String    getCommitmentId()      { return commitmentId; }
    public String    getVendor()            { return vendor; }
    public String    getAccountId()         { return accountId; }
    public LocalDate getLatestPeriodDay()   { return latestPeriodDay; }
    public Double    getLatestUsedUsd()     { return latestUsedUsd; }
    public Double    getLatestAvailableUsd(){ return latestAvailableUsd; }
    public Double    getLatestFraction()    { return latestFraction; }
    public String    getStatus()            { return status; }
    public String    getLastAlertType()     { return lastAlertType; }
    public OffsetDateTime getLastAlertAt()  { return lastAlertAt; }
    public UUID      getLastAlertEventId()  { return lastAlertEventId; }
}
