# =============================================================================
# FinFlow — convenience commands
# Usage: `make up`, `make logs`, `make smoke`, etc.
# =============================================================================

.PHONY: help up down restart logs ps clean smoke psql kafka-cli health build

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

up: ## Start the full infrastructure stack
	docker compose up -d
	@echo "Waiting for services to become healthy..."
	@sleep 5
	@$(MAKE) ps

down: ## Stop everything (preserves volumes)
	docker compose down

restart: down up ## Restart everything

clean: ## Stop everything and wipe ALL volumes (full reset)
	docker compose down -v
	@echo "Volumes wiped. Next 'make up' will be a fresh start."

logs: ## Tail logs from all services
	docker compose logs -f

ps: ## Show container status
	docker compose ps

health: ## Show health status of all services
	@docker compose ps --format "table {{.Service}}\t{{.Status}}"

smoke: ## Run the day 1 smoke test
	@echo "=== Postgres ==="
	@docker compose exec postgres psql -U finflow -d finflow -c "SELECT version();" | head -3
	@docker compose exec postgres psql -U finflow -d finflow -c "SELECT name, setting FROM pg_settings WHERE name IN ('wal_level','max_replication_slots');"
	@echo ""
	@echo "=== Kafka ==="
	@docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
	@echo ""
	@echo "=== UIs ==="
	@echo "Kafka UI:  http://localhost:8090"
	@echo "pgAdmin:   http://localhost:8091  (dev@finflow.local / finflow)"
	@echo "Toxiproxy: http://localhost:8474/version"

psql: ## Open a psql shell against the dev database
	docker compose exec postgres psql -U finflow -d finflow

kafka-cli: ## Open a bash shell in the Kafka container (for kafka-* commands)
	docker compose exec kafka bash

build: ## Run a Gradle build (will be useful once modules exist)
	./gradlew build
