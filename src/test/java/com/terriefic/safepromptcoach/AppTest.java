package com.terriefic.safepromptcoach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the real server on a free port and calls it over HTTP. */
class AppTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private Javalin app;

    @BeforeEach
    void startServer() {
        app = App.create(new CoachService()).start(0);
    }

    @AfterEach
    void stopServer() {
        app.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }

    @Test
    void rootServesTheCoachPage() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("<title>AI Without the Fear: Safe Prompt Coach</title>"));
    }

    @Test
    void pageAssetsAreServed() throws Exception {
        for (String path : new String[] {"/styles.css", "/limits.js", "/app.js"}) {
            assertEquals(200, get(path).statusCode(), path);
        }
    }

    @Test
    void everyResponseHasTheContentSecurityPolicy() throws Exception {
        HttpResponse<String> page = get("/");
        HttpResponse<String> health = get("/health");
        HttpResponse<String> coach = post("/api/coach", "{\"goal\": \"draft\", \"taskDescription\": \"A note\"}");
        HttpResponse<String> error = post("/api/coach", "not json");

        for (HttpResponse<String> response : List.of(page, health, coach, error)) {
            assertEquals(App.CONTENT_SECURITY_POLICY,
                    response.headers().firstValue("Content-Security-Policy").orElse(null),
                    response.uri().getPath());
        }
    }

    @Test
    void healthReturnsOk() throws Exception {
        HttpResponse<String> response = get("/health");
        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }

    @Test
    void coachReturnsPromptChecklistAndVerificationStep() throws Exception {
        HttpResponse<String> response = post("/api/coach", """
                {"goal": "summarize", "audience": "my book club",
                 "taskDescription": "Summarize this article about gardening", "sensitive": false}
                """);

        assertEquals(200, response.statusCode());
        JsonNode json = mapper.readTree(response.body());
        assertEquals("summarize", json.get("goal").asText());
        assertTrue(json.get("suggestedPrompt").asText().contains("my book club"));
        assertEquals(5, json.get("pauseChecklist").size());
        assertEquals("P", json.get("pauseChecklist").get(0).get("letter").asText());
        assertEquals(Goal.SUMMARIZE.verificationStep(), json.get("verificationStep").asText());
        assertTrue(json.get("privacyReminder").isNull());
    }

    @Test
    void sensitiveTaskIncludesPrivacyReminder() throws Exception {
        HttpResponse<String> response = post("/api/coach", """
                {"goal": "draft", "taskDescription": "A letter to my bank", "sensitive": true}
                """);

        assertEquals(200, response.statusCode());
        assertEquals(CoachService.PRIVACY_REMINDER, mapper.readTree(response.body()).get("privacyReminder").asText());
    }

    @Test
    void tooLongTaskReturns400() throws Exception {
        String task = "a".repeat(CoachService.MAX_TASK_LENGTH + 1);
        HttpResponse<String> response = post("/api/coach",
                "{\"goal\": \"draft\", \"taskDescription\": \"" + task + "\"}");

        assertEquals(400, response.statusCode());
        assertEquals("taskDescription must be 1000 characters or fewer.",
                mapper.readTree(response.body()).get("error").asText());
    }

    @Test
    void unknownGoalReturns400() throws Exception {
        HttpResponse<String> response = post("/api/coach", """
                {"goal": "hack", "taskDescription": "Something"}
                """);
        assertEquals(400, response.statusCode());
    }

    @Test
    void malformedJsonReturns400WithoutEchoingInput() throws Exception {
        HttpResponse<String> response = post("/api/coach", "{\"goal\": \"draft\", \"taskDescription\": \"my PIN 4321");

        assertEquals(400, response.statusCode());
        assertEquals("Request body must be valid JSON.", mapper.readTree(response.body()).get("error").asText());
        assertFalse(response.body().contains("4321"));
    }

    @Test
    void unknownFieldReturns400ListingTheAllowedFields() throws Exception {
        HttpResponse<String> response = post("/api/coach", """
                {"goal": "draft", "audience": "me", "task": "test run", "sensitive": false}
                """);

        assertEquals(400, response.statusCode());
        assertEquals("Unknown field in request. Allowed fields: goal, audience, taskDescription, sensitive.",
                mapper.readTree(response.body()).get("error").asText());
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        assertEquals(400, post("/api/coach", "").statusCode());
    }
}
