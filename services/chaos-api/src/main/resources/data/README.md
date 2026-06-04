# AWS CUR LineItems Fixture (aws-cur-lineitems.json)

### Purpose
This file is a "fixture" (mock data) representing a simulated AWS Cost & Usage Report (CUR). It acts as a fake AWS bill so the `chaos-api` can serve realistic billing data to the FinFlow ingestor without needing to connect to a real, paid AWS account.

### What is being tested?
1. **Jackson `@JsonProperty` Mapping:** The keys use real AWS dotted/colon notation (e.g., `identity/LineItemId`). This ensures our Java DTOs successfully map these illegal Java characters into standard variables.
2. **The `non_null` Behavior:** Items 9–11 (RIFee, Tax, Credit) deliberately omit keys like `reservation/*` and `resourceTags/*`. This tests that our Spring Boot API correctly strips missing fields from the final JSON payload rather than outputting `"field": null`.

### The "Northwind" Test Scenario
The 12 items simulate a realistic company architecture to test different billing logic-
1. Items 1-7: Heavy machine learning EC2 instances (`p3` and `p5`), testing both On-Demand and Discounted pricing.
2. Item 8: A standard API server covered by an AWS Savings Plan.
3. Items 9-11: Account-level financial adjustments (Upfront Reservation Fees, Taxes, and API Credits).
4. Item 12: Standard S3 bucket storage usage.