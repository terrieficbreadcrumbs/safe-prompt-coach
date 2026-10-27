package com.terriefic.safepromptcoach;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** Starts the web server and wires the routes to CoachService. */
public class App {

    private static final int DEFAULT_PORT = 8080;

    /** The JSON field names of CoachRequest, e.g. "goal, audience, taskDescription, sensitive". */
    private static final String ALLOWED_FIELDS = Arrays.stream(CoachRequest.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.joining(", "));

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

    /** Reads the JSON body. Error messages never repeat what the user sent, not even an unknown field's name. */
    private static CoachRequest parseBody(Context ctx) {
        try {
            return ctx.bodyAsClass(CoachRequest.class);
        } catch (Exception e) {
            if (hasCause(e, UnrecognizedPropertyException.class)) {
                throw new ValidationException("Unknown field in request. Allowed fields: " + ALLOWED_FIELDS + ".");
            }
            throw new ValidationException("Request body must be valid JSON.");
        }
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
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
