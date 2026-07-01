package io.finflow.normalizer.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The metadata mapping a cloud account/billing-account id to the team that owns
 * it. Read-only at runtime — seeded by the V2 migration. The normalizer joins
 * this in for every line item to enrich {@code team} and {@code costCenter}.
 *
 * <p>Plain mutable JPA class (entities can't be records), matching the project
 * convention.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "vendor", length = 16, nullable = false)
    private String vendor;

    @Column(name = "team", length = 64, nullable = false)
    private String team;

    @Column(name = "cost_center", length = 64)
    private String costCenter;

    protected Account() { /* for JPA */ }

    public String getAccountId()  { return accountId; }
    public String getVendor()     { return vendor; }
    public String getTeam()       { return team; }
    public String getCostCenter() { return costCenter; }
}
