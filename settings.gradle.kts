rootProject.name = "finflow"

include(
    "shared:outbox-starter",
    "services:aws-ingestor",
    "services:gcp-ingestor",
    "services:cost-normalizer",
    "services:commitment-tracker",
    "services:recommendation-engine",
    "services:saga-orchestrator",
    "services:aws-adapter-worker",
    "services:gcp-adapter-worker",
    "services:query-api"
)