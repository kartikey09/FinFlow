# GCP Billing Row — Field Reference

**billing_account_id** — `"01ABCD-234567-89EFGH"` — The GCP billing account that pays for this usage. One billing account can fund many projects; it's the top of the payment hierarchy (rough analogue of AWS's payer account).

**service** — `{ id, description }` — What product this charge is for. The `id` (`6F81-5844-456A`) is GCP's stable internal identifier; the `description` (`Compute Engine`) is the human-readable name. Nested object, not a flat string — that's the GCP-vs-AWS difference.

**sku** — `{ id, description }` — The specific priced item within that service. Where service says "Compute Engine," the SKU says exactly which billable thing: `A2 Instance Core running in Americas`. This is GCP's closest equivalent to AWS's instance-type field, and it's where your normalizer digs out the machine family.

**usage_start_time / usage_end_time** — `"...T10:00:00Z"` / `"...T11:00:00Z"` — The time window this row covers. Here it's a single hour. These bound when the usage happened (the normalizer uses them to bucket spend by day/hour).

**project** — `{ id, name }` — Which project incurred the cost. `northwind-platform` is the Platform team's project. A GCP project is the rough equivalent of an AWS account and is the unit spend gets keyed on.

**labels** — array of `{ key, value }` — User-applied tags. Here, `team=platform` and `cost_center=CC-100`. This is GCP's version of AWS resource tags, but structurally an array you walk, not flat columns — the normalizer scans it looking for the `team` key.

**cost** — `12.24` — The list (pre-discount) price for this row, in the billing currency. Important: this is before credits are applied — it is not what you actually paid.

**currency** — `"USD"` — The currency `cost` is expressed in.

**currency_conversion_rate** — `1.0` — Multiplier to convert `cost` into the billing account's base currency. `1.0` means already in base (USD here); a EUR row would carry something like `1.08`.

**usage** — `{ amount, unit, amount_in_pricing_units, pricing_unit }` — How much was consumed, expressed two ways. The raw measure (`3600 seconds`) and the pricing-normalized measure (`1.0 hour`). The normalizer generally wants the pricing-units value (hours), not the raw seconds.

**credits** — array of `{ name, amount, type }` — Discounts applied to this row, as negative amounts. Here a `-8.16` `COMMITTED_USAGE_DISCOUNT`. This is the single biggest structural difference from AWS: the discount lives on the same row as the cost, so your real net cost = `cost + sum(credits[].amount)` = `12.24 + (−8.16) = 4.08`. There's no single "effective cost" field to read — you must sum the array.

**cost_type** — `"regular"` — The kind of charge. `regular` is normal usage; other values include `tax`, `adjustment`, and `rounding_error`. Lets the normalizer treat non-usage rows differently.
