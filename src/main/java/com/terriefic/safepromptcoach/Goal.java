package com.terriefic.safepromptcoach;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** The four tasks the coach supports, each with its own prompt instructions and verification step. */
public enum Goal {

    DRAFT(
            "Please help me write a first draft for %s.",
            "Use a clear, friendly tone and plain language. Keep it short. "
                    + "Mark anything you're unsure about with [CHECK] so I can review it before I use it.",
            "Read the whole draft yourself and fix anything that isn't true or doesn't sound like you before you share it."),

    SUMMARIZE(
            "Please summarize the following for %s.",
            "Use short bullet points in plain language. Only use what's in the material I give you, "
                    + "and don't add outside facts. If something is unclear, say so instead of guessing.",
            "Compare the summary to the original and confirm every key point actually appears there."),

    ORGANIZE(
            "Please help me organize the following for %s.",
            "Group related items under simple headings and put them in a sensible order. "
                    + "Don't add or remove anything. If something seems missing or doesn't fit, tell me.",
            "Check the organized version against your original to make sure nothing was dropped, added, or changed."),

    EXPLAIN(
            "Please explain the following for %s.",
            "Use plain language and an everyday example. Tell me which parts I should double-check "
                    + "with a trusted source, and say so if you're not sure about something.",
            "Look up the main facts in a trusted source, such as an official website, a librarian, "
                    + "or a professional, before you rely on the explanation.");

    private final String opening;
    private final String instructions;
    private final String verificationStep;

    Goal(String opening, String instructions, String verificationStep) {
        this.opening = opening;
        this.instructions = instructions;
        this.verificationStep = verificationStep;
    }

    public String opening(String audience) {
        return opening.formatted(audience);
    }

    public String instructions() {
        return instructions;
    }

    public String verificationStep() {
        return verificationStep;
    }

    /** The lowercase name used in the JSON API, e.g. "summarize". */
    public String apiName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive lookup; empty if the value isn't one of the four goals. */
    public static Optional<Goal> fromApiName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(g -> g.name().equals(normalized)).findFirst();
    }

    /** "draft, summarize, organize, explain" for error messages. */
    public static String validNames() {
        return Arrays.stream(values()).map(Goal::apiName).collect(Collectors.joining(", "));
    }
}
