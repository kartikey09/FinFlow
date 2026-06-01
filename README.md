# FinFlow

A FinOps reconciliation platform built around the outbox + CDC + saga patterns.
Demonstrates event-driven reliability across 8 Spring Boot services with AWS and
GCP billing ingestion, fault-injected through a purpose-built Chaos API.

**Status:** Under construction · Week 1 of 6 · Foundations phase

---

## Quick start

Requires: JDK 21, Docker Desktop with ≥8 GB RAM allocated, ~10 GB disk free.

```bash
# bring up infrastructure
make up

# verify it's healthy
make smoke

# bring it down (preserves data)
make down

# nuclear option (wipes all volumes for a clean restart)
make clean
```

After `make up`, four UIs are available:

| URL                          | What it is                                  |
|------------------------------|---------------------------------------------|
| http://localhost:8090        | **Kafka UI** — topic and message inspection |
| http://localhost:8091        | **pgAdmin** — login `dev@finflow.local` / `finflow` |
| http://localhost:8474/version | **Toxiproxy admin** — network chaos control |

---

## Local infrastructure ports

| Service       | Host port | Container | Purpose                              |
|---------------|-----------|-----------|--------------------------------------|
| Postgres 16   | 5432      | 5432      | Primary DB, logical WAL enabled      |
| Kafka 3.7     | 29092     | 9092      | Event bus (KRaft mode, no Zookeeper) |
| Kafka UI      | 8090      | 8080      | Web UI for Kafka                     |
| pgAdmin       | 8091      | 80        | Web UI for Postgres                  |
| Toxiproxy API | 8474      | 8474      | Chaos injection admin                |

When connecting from **inside Docker** (containerized services): use `kafka:9092`
and `postgres:5432`. When connecting from your **host machine** (e.g. a Spring
Boot service launched from IntelliJ): use `localhost:29092` and `localhost:5432`.

---

## Service ports (reserved — built across weeks 2-6)

| Service                | Port  | Built by    |
|------------------------|-------|-------------|
| chaos-api              | 9000  | Days 2-4    |
| aws-ingestor           | 8081  | Day 11      |
| gcp-ingestor           | 8082  | Day 12      |
| cost-normalizer        | 8083  | Day 13      |
| commitment-tracker     | 8084  | Day 14      |
| recommendation-engine  | 8085  | Day 26      |
| saga-orchestrator      | 8086  | Day 17      |
| aws-adapter-worker     | 8087  | Day 18      |
| gcp-adapter-worker     | 8088  | Day 19      |
| query-api              | 8089  | Day 15      |
| React dashboard        | 3000  | Day 15      |

---

## Repository layout

```
finflow/
├── docker-compose.yml          # local infra: Postgres, Kafka, UIs, Toxiproxy
├── Makefile                    # convenience commands
├── settings.gradle.kts         # monorepo module declarations
├── build.gradle.kts            # shared build config
├── gradle.properties           # build performance + JVM tuning
├── infra/docker/               # Docker init scripts and config
├── shared/                     # shared libraries (outbox-starter, etc.)
└── services/                   # one directory per Spring Boot service
```

---

## Roadmap

- [x] **Day 1** — Repo skeleton + Docker Compose infrastructure
- [ ] **Days 2-4** — Chaos API (AWS + GCP fake endpoints with fault injection)
- [ ] **Day 5** — First Spring Boot service shell (`aws-ingestor`)
- [ ] **Week 2** — Outbox pattern + Debezium CDC + atomicity tests
- [ ] **Week 3** — Real ingestion + normalization + dashboard
- [ ] **Week 4** — Saga orchestration + compensation + recovery
- [ ] **Week 5** — Resilience4j + Prometheus + Grafana + Jaeger + chaos suite
- [ ] **Week 6** — Recommendations + README polish + demo video + ship

---

## Architecture

See the [build plan document](docs/) for the full architecture write-up. In one
sentence: ingestors poll the Chaos API for billing data and publish via the
outbox pattern; Debezium tails the Postgres WAL into Kafka; downstream services
build CQRS read models and execute saga-orchestrated commitment rebalances.

---

## License

MIT (will add LICENSE file before public push)
