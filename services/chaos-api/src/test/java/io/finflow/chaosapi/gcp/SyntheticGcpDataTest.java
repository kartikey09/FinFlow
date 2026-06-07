package io.finflow.chaosapi.gcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.chaosapi.gcp.dto.GcpBillingRow;
import io.finflow.chaosapi.gcp.dto.GcpCredit;
import io.finflow.chaosapi.gcp.dto.GcpLabel;
import io.finflow.chaosapi.gcp.dto.SyntheticGcpData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the GCP fixture loads and holds the cross-cloud FinFlow
 * invariants. Calls the package-private load() directly — no Spring context.
 */
class SyntheticGcpDataTest {

    private SyntheticGcpData data;

    @BeforeEach
    void setUp() throws Exception {
        data = new SyntheticGcpData(new ObjectMapper());
        data.load();
    }

    @Test
    void loadsExactlyTenRows() {
        assertThat(data.all()).hasSize(10);
    }

    @Test
    void everyRowHasAServiceAndProject() {
        assertThat(data.all()).allSatisfy(row -> {
            assertThat(row.service()).isNotNull();
            assertThat(row.service().description()).isNotBlank();
            assertThat(row.project()).isNotNull();
            assertThat(row.project().id()).isNotBlank();
        });
    }

    @Test
    void platformRows_carryACommittedUseDiscountCredit() {
        List<GcpBillingRow> platform = teamRows("platform");
        assertThat(platform).isNotEmpty();

        // The compute rows (not the commitment-fee row) should each have a CUD credit
        List<GcpBillingRow> platformCompute = platform.stream()
                .filter(r -> "A2 Instance Core running in Americas".equals(r.sku().description()))
                .toList();

        assertThat(platformCompute).isNotEmpty();
        assertThat(platformCompute).allSatisfy(r -> {
            boolean hasCud = r.credits().stream()
                    .anyMatch(c -> "COMMITTED_USAGE_DISCOUNT".equals(c.type()));
            assertThat(hasCud).isTrue();
        });
    }

    @Test
    void ingestionComputeRows_areOnDemandWithNoCredits() {
        List<GcpBillingRow> ingestionCompute = teamRows("ingestion").stream()
                .filter(r -> "A2 Instance Core running in Americas".equals(r.sku().description()))
                .toList();

        assertThat(ingestionCompute).isNotEmpty();
        assertThat(ingestionCompute).allSatisfy(r ->
                assertThat(r.credits()).isEmpty());   // the overspend: no discount applied
    }

    @Test
    void platformNetCost_isCheaperThanIngestionForSameSku() {
        double platformNet = teamRows("platform").stream()
                .filter(r -> "A2 Instance Core running in Americas".equals(r.sku().description()))
                .mapToDouble(this::netCost).sum();
        double ingestionNet = teamRows("ingestion").stream()
                .filter(r -> "A2 Instance Core running in Americas".equals(r.sku().description()))
                .mapToDouble(this::netCost).sum();

        // Same machine type, but Platform's CUD makes its effective cost lower —
        // this is the cross-cloud mirror of the AWS $10.20-vs-$24.48 story.
        assertThat(platformNet).isLessThan(ingestionNet);
    }

    @Test
    void containsANonUsdRow_toExerciseCurrencyConversion() {
        boolean hasNonUsd = data.all().stream()
                .anyMatch(r -> !"USD".equals(r.currency()));
        assertThat(hasNonUsd).isTrue();
    }

    @Test
    void containsMultipleCreditTypes() {
        List<String> creditTypes = data.all().stream()
                .flatMap(r -> r.credits().stream())
                .map(GcpCredit::type)
                .distinct()
                .toList();
        // CUD, SUD, and FREE_TIER all appear in the fixture
        assertThat(creditTypes).contains(
                "COMMITTED_USAGE_DISCOUNT", "SUSTAINED_USAGE_DISCOUNT", "FREE_TIER");
    }

    // --- helpers ---

    private List<GcpBillingRow> teamRows(String team) {
        return data.all().stream().filter(r -> teamLabel(r).map(team::equals).orElse(false)).toList();
    }

    private Optional<String> teamLabel(GcpBillingRow row) {
        if (row.labels() == null) return Optional.empty();
        return row.labels().stream()
                .filter(l -> "team".equals(l.key()))
                .map(GcpLabel::value)
                .findFirst();
    }

    private double netCost(GcpBillingRow row) {
        return row.cost() + row.credits().stream().mapToDouble(GcpCredit::amount).sum();
    }
}
