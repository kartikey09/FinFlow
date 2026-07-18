package io.finflow.recommendation.api;

import io.finflow.recommendation.store.Recommendation;
import io.finflow.recommendation.store.RecommendationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/recommendations — the OPEN recommendations, highest estimated savings
 * first. This is a convenience read directly off the engine's own store; the dashboard
 * proper reads the same data via query-api's projection (fed by
 * finflow.events.recommendation). Having both means the engine is inspectable on its
 * own without standing up the whole read side.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationRepository repository;

    public RecommendationController(RecommendationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RecommendationDto> top(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<Recommendation> open = repository.findTopOpen();
        return open.stream()
                .limit(Math.max(1, limit))
                .map(RecommendationDto::from)
                .toList();
    }
}
