package io.finflow.chaosapi.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.chaosapi.aws.dto.AwsCurLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the fixture loads and holds the Northwind invariants. Calls the
 * package-private load() directly — no Spring context, fast and dependency-free.
 */
class SyntheticCurDataTest {

    private SyntheticCurData data;

    @BeforeEach
    void setUp() throws Exception {
        data = new SyntheticCurData(new ObjectMapper());
        data.load();
    }

    @Test
    void loadsExactlyTwelveLineItems() {
        assertThat(data.all()).hasSize(12);
    }

    @Test
    void everyItemHasAnIdempotencyKey() {
        assertThat(data.all())
                .allSatisfy(item -> assertThat(item.lineItemId()).isNotBlank());
    }

    @Test
    void lineItemIdsAreUnique() {
        Set<String> ids = data.all().stream()
                .map(AwsCurLineItem::lineItemId)
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(12);
    }

    @Test
    void coversAllSixLineItemTypes() {
        Set<String> types = data.all().stream()
                .map(AwsCurLineItem::lineItemType)
                .collect(Collectors.toSet());
        assertThat(types).containsExactlyInAnyOrder(
                "Usage", "DiscountedUsage", "SavingsPlanCoveredUsage",
                "RIFee", "Tax", "Credit");
    }

    @Test
    void platformRiRows_areDiscountedUsageWithZeroUnblendedButRealEffectiveCost() {
        List<AwsCurLineItem> platformRi = data.all().stream()
                .filter(i -> "DiscountedUsage".equals(i.lineItemType()))
                .toList();
        assertThat(platformRi).isNotEmpty();
        assertThat(platformRi).allSatisfy(i -> {
            assertThat(i.unblendedCost()).isEqualTo(0.0);
            assertThat(i.reservationEffectiveCost()).isEqualTo(10.20);
            assertThat(i.tagTeam()).isEqualTo("platform");
        });
    }

    @Test
    void ingestionOnDemandRows_carryFullOnDemandCost() {
        List<AwsCurLineItem> ingestionOnDemand = data.all().stream()
                .filter(i -> "ingestion".equals(i.tagTeam()))
                .filter(i -> "p3.16xlarge".equals(i.instanceType()))
                .toList();
        assertThat(ingestionOnDemand).isNotEmpty();
        assertThat(ingestionOnDemand).allSatisfy(i ->
                assertThat(i.unblendedCost()).isEqualTo(24.48));
    }

    @Test
    void creditRow_hasNegativeCost() {
        AwsCurLineItem credit = data.all().stream()
                .filter(i -> "Credit".equals(i.lineItemType()))
                .findFirst().orElseThrow();
        assertThat(credit.unblendedCost()).isLessThan(0.0);
    }
}
