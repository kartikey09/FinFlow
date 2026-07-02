package io.finflow.query.projection.spend;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite key for {@link SpendByTeamDay}: one row per (tenant, team, day). */
public class SpendByTeamDayKey implements Serializable {
    private String tenantId;
    private String team;
    private LocalDate periodDay;

    public SpendByTeamDayKey() { /* for JPA */ }

    public SpendByTeamDayKey(String tenantId, String team, LocalDate periodDay) {
        this.tenantId = tenantId; this.team = team; this.periodDay = periodDay;
    }

    public String getTenantId()   { return tenantId; }
    public String getTeam()       { return team; }
    public LocalDate getPeriodDay(){ return periodDay; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpendByTeamDayKey k)) return false;
        return Objects.equals(tenantId, k.tenantId)
            && Objects.equals(team, k.team)
            && Objects.equals(periodDay, k.periodDay);
    }
    @Override public int hashCode() { return Objects.hash(tenantId, team, periodDay); }
}
