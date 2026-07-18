package io.finflow.query.api;

import io.finflow.query.projection.recommendation.RecommendationView;
import io.finflow.query.projection.recommendation.RecommendationViewRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/recommendations — the dashboard's recommendation feed, highest estimated
 * savings first. Reads read_model.mv_recommendation, projected from
 * finflow.events.recommendation by {@link io.finflow.query.consumer.RecommendationEventConsumer}.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationViewRepository repository;

    public RecommendationController(RecommendationViewRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RecommendationDto> top(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return repository.findAllByOrderByEstimatedSavingsUsdDesc().stream()
                .limit(Math.max(1, limit))
                .map(RecommendationDto::from)
                .toList();
    }

    public record RecommendationDto(
            String id, String type, String commitmentId, String accountId,
            String vendor, String service, String region, double estimatedSavingsUsd, String evidence) {

        static RecommendationDto from(RecommendationView r) {
            return new RecommendationDto(
                    r.getId().toString(), r.getType(), r.getCommitmentId(), r.getAccountId(),
                    r.getVendor(), r.getService(), r.getRegion(),
                    r.getEstimatedSavingsUsd(), r.getEvidence());
        }
    }
}
