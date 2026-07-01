package io.finflow.normalizer.accounts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caching lookup of account metadata, used by the normalizers to enrich every
 * line item with team / cost-center.
 *
 * <p>Why cache: the accounts table is small (handfuls of rows) but every line
 * item triggers a lookup, so it's a hot read. A small Caffeine cache with a
 * 10-minute expiry absorbs the load and lets edits to the table (manual today,
 * a real service later) propagate within minutes.
 *
 * <p>Missing or unknown account ids return a safe placeholder ("unassigned" /
 * {@code null} cost-center) instead of throwing — an unmapped account should
 * never wedge ingestion. This is recorded as a WARN in logs so it surfaces.
 */
@Service
public class AccountLookupService {

    /** Returned when an account id is unknown — caller treats it as an enrichment miss. */
    public static final Account UNKNOWN_ACCOUNT = unknown();

    private final AccountRepository repository;
    private final Cache<String, Account> cache;

    public AccountLookupService(AccountRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    /** Look up account metadata, falling back to {@link #UNKNOWN_ACCOUNT} if absent. */
    public Account findOrUnknown(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return UNKNOWN_ACCOUNT;
        }
        return cache.get(accountId,
                id -> repository.findById(id).orElse(UNKNOWN_ACCOUNT));
    }

    private static Account unknown() {
        // A tiny anonymous subclass so we can populate read-only getters without
        // exposing a public setter API for production code.
        return new Account() {
            @Override public String getTeam()       { return "unassigned"; }
            @Override public String getCostCenter() { return null; }
            @Override public String getVendor()     { return "unknown"; }
            @Override public String getAccountId()  { return null; }
        };
    }
}
