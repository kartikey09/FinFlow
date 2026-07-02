package io.finflow.query.projection.spend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the pre-aggregated spend projection. Incrementally maintained by
 * {@link io.finflow.query.consumer.CostNormalizedEventConsumer}: each
 * CostLineItemNormalized adds its cost_usd to the (tenant, team, day) total.
 *
 * <p>Idempotency: {@code last_event_id} catches a redelivery of THE SAME event
 * to THE SAME (team, day) row — cheap because we already looked up the row.
 * The trade-off is the same as commitment-tracker's: a redelivery arriving
 * after a different event has bumped the row can't be detected. Acceptable at
 * the projection layer; the ledger and outbox above are the sources of truth.
 */
@Entity
@Table(name = "mv_spend_by_team_day")
@IdClass(SpendByTeamDayKey.class)
public class SpendByTeamDay {

    @Id @Column(name = "tenant_id", length = 64) private String tenantId;
    @Id @Column(name = "team",      length = 64) private String team;
    @Id @Column(name = "period_day")             private LocalDate periodDay;

    @Column(name = "cost_usd", nullable = false)      private Double costUsd = 0.0;
    @Column(name = "event_count", nullable = false)   private Integer eventCount = 0;
    @Column(name = "last_event_id")                   private UUID lastEventId;
    @Column(name = "updated_at", nullable = false)    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected SpendByTeamDay() { /* for JPA */ }

    public SpendByTeamDay(String tenantId, String team, LocalDate periodDay) {
        this.tenantId = tenantId; this.team = team; this.periodDay = periodDay;
    }

    public void addSpend(double delta, UUID eventId) {
        this.costUsd += delta;
        this.eventCount += 1;
        this.lastEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    public String    getTenantId()    { return tenantId; }
    public String    getTeam()        { return team; }
    public LocalDate getPeriodDay()   { return periodDay; }
    public Double    getCostUsd()     { return costUsd; }
    public Integer   getEventCount()  { return eventCount; }
    public UUID      getLastEventId() { return lastEventId; }
}
