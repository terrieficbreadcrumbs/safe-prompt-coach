package com.terriefic.safepromptcoach;

import java.util.List;

/** The JSON returned by POST /api/coach. {@code privacyReminder} is null unless the task is sensitive. */
public record CoachResponse(
        String goal,
        String suggestedPrompt,
        List<PauseRule> pauseChecklist,
        String verificationStep,
        String privacyReminder) {
}
