package io.finflow.recommendation.store;

import java.io.Serializable;
import java.util.Objects;

/** Composite PK for {@link OndemandSpendRollup}: (tenant, account, service). */
public class OndemandSpendRollupKey implements Serializable {

    private String tenantId;
    private String accountId;
    private String service;

    public OndemandSpendRollupKey() {}

    public OndemandSpendRollupKey(String tenantId, String accountId, String service) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OndemandSpendRollupKey k)) return false;
        return Objects.equals(tenantId, k.tenantId)
                && Objects.equals(accountId, k.accountId)
                && Objects.equals(service, k.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, accountId, service);
    }
}
