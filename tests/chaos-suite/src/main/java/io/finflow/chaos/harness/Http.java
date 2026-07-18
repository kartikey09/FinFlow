package io.finflow.chaos.harness;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Thin wrapper over the JDK's HttpClient. No HTTP library dependency needed. */
final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Http() {}

    static HttpResponse<String> post(String url, String jsonBody) {
        HttpRequest.BodyPublisher body = (jsonBody == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(body).build());
    }

    static HttpResponse<String> get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build());
    }

    static HttpResponse<String> delete(String url) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).DELETE().build());
    }

    /** Non-throwing probe — used by health polling, where "connection refused" IS the answer. */
    static boolean isUp(String url) {
        try {
            return get(url).statusCode() == 200;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static HttpResponse<String> send(HttpRequest req) {
        try {
            return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(req.method() + " " + req.uri() + " failed: " + e, e);
        }
    }
}
