package io.finflow.query.api;

import io.finflow.query.projection.spend.SpendByTeamDayRepository;
import io.finflow.query.projection.spend.SpendByTeamDayRepository.SpendByTeamAggregate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/spend/by-team?period=last-30d
 *
 * <p>Returns a list of {@code {team, costUsd}} rows, already summed across the
 * window at the database. Serves the dashboard's bar chart.
 */
@RestController
@RequestMapping("/api/v1/spend")
public class SpendController {

    private final SpendByTeamDayRepository repository;
    private final PeriodParser periodParser = new PeriodParser();
    private final String tenantId;

    public SpendController(SpendByTeamDayRepository repository,
                           @Value("${finflow.tenant-id:default}") String tenantId) {
        this.repository = repository;
        this.tenantId = tenantId;
    }

    @GetMapping("/by-team")
    public List<SpendByTeamDto> byTeam(@RequestParam(name = "period", defaultValue = "last-30d") String period) {
        PeriodParser.Window window = periodParser.parse(period);
        List<SpendByTeamAggregate> rows = repository.sumByTeamInWindow(tenantId, window.from(), window.to());
        return rows.stream()
                .map(r -> new SpendByTeamDto(r.getTeam(),
                                             r.getCostUsd() == null ? 0.0 : r.getCostUsd()))
                .toList();
    }
}
