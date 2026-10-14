package com.terriefic.safepromptcoach;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/** Starts the web server and wires the routes to CoachService. */
public class App {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        create(new CoachService()).start(port());
    }

    /** Builds the app without starting it, so tests can start it on any free port. */
    public static Javalin create(CoachService coachService) {
        return Javalin.create(config -> {
            config.routes.get("/health", ctx -> ctx.result("ok"));

            config.routes.post("/api/coach", ctx -> {
                CoachRequest request = parseBody(ctx);
                ctx.json(coachService.coach(request));
            });

            config.routes.exception(ValidationException.class, (e, ctx) -> sendError(ctx, e.getMessage()));
        });
    }

    /** Reads the JSON body. The error message never repeats what the user sent. */
    private static CoachRequest parseBody(Context ctx) {
        try {
            return ctx.bodyAsClass(CoachRequest.class);
        } catch (Exception e) {
            throw new ValidationException("Request body must be valid JSON.");
        }
    }

    private static void sendError(Context ctx, String message) {
        ctx.status(400).json(Map.of("error", message));
    }

    /** Cloud Run tells the app which port to use through PORT. This is the only environment variable read. */
    private static int port() {
        String value = System.getenv("PORT");
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(value.trim());
    }
}
