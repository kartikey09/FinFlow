package io.finflow.query.projection.spend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpendByTeamDayRepository extends JpaRepository<SpendByTeamDay, SpendByTeamDayKey> {

    /**
     * Sum spend by team across the requested window. This is what the dashboard's
     * bar chart consumes — one row per team, already summed at the DB.
     */
    @Query("""
            SELECT s.team AS team, SUM(s.costUsd) AS costUsd
            FROM SpendByTeamDay s
            WHERE s.tenantId = :tenantId
              AND s.periodDay >= :from
              AND s.periodDay <= :to
            GROUP BY s.team
            ORDER BY SUM(s.costUsd) DESC
            """)
    List<SpendByTeamAggregate> sumByTeamInWindow(
            @Param("tenantId") String tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** JPA projection interface — Spring Data materializes anonymous rows into this. */
    interface SpendByTeamAggregate {
        String getTeam();
        Double getCostUsd();
    }
}
