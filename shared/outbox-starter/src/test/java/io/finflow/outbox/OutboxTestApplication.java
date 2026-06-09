package io.finflow.outbox;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal boot app for the library's own tests. Living in io.finflow.outbox
 * means Spring's defaults scan THIS package, so the OutboxEvent entity and
 * OutboxEventRepository are discovered without any explicit @EntityScan —
 * the auto-config (via the imports file) then supplies the OutboxAppender.
 */
@SpringBootApplication
public class OutboxTestApplication {
}
