package com.terriefic.safepromptcoach;

/** The rule-based coach: checks a request and builds a safe prompt, the PAUSE checklist, and a verification step. */
public class CoachService {

    public static final int MAX_TASK_LENGTH = 1000;
    public static final int MAX_AUDIENCE_LENGTH = 200;
    public static final String DEFAULT_AUDIENCE = "a general reader";

    static final String PLACEHOLDER_NOTE =
            "I've replaced private details with placeholders like [NAME], [ADDRESS], and [ACCOUNT #]. "
                    + "Please keep those placeholders as they are.";

    static final String PRIVACY_REMINDER =
            "Before you paste this into an AI tool, replace names, addresses, account numbers, health details, "
                    + "and anyone else's information with placeholders like [NAME] or [ACCOUNT #].";

    public CoachResponse coach(CoachRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required.");
        }

        Goal goal = Goal.fromApiName(request.goal())
                .orElseThrow(() -> new ValidationException("goal must be one of: " + Goal.validNames() + "."));

        String task = request.taskDescription() == null ? "" : request.taskDescription().strip();
        if (task.isEmpty()) {
            throw new ValidationException("taskDescription is required.");
        }
        if (length(task) > MAX_TASK_LENGTH) {
            throw new ValidationException("taskDescription must be " + MAX_TASK_LENGTH + " characters or fewer.");
        }

        String audience = request.audience() == null ? "" : request.audience().strip();
        if (audience.isEmpty()) {
            audience = DEFAULT_AUDIENCE;
        }
        if (length(audience) > MAX_AUDIENCE_LENGTH) {
            throw new ValidationException("audience must be " + MAX_AUDIENCE_LENGTH + " characters or fewer.");
        }

        boolean sensitive = Boolean.TRUE.equals(request.sensitive());

        StringBuilder prompt = new StringBuilder()
                .append(goal.opening(audience))
                .append("\n\nTask: ").append(task)
                .append("\n\n").append(goal.instructions());
        if (sensitive) {
            prompt.append("\n\n").append(PLACEHOLDER_NOTE);
        }

        return new CoachResponse(
                goal.apiName(),
                prompt.toString(),
                PauseRule.CHECKLIST,
                goal.verificationStep(),
                sensitive ? PRIVACY_REMINDER : null);
    }

    /** Counts real characters, so an emoji counts as one, not two. */
    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }
}
