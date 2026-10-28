package com.terriefic.safepromptcoach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachServiceTest {

    private final CoachService service = new CoachService();

    private static CoachRequest request(String goal, String audience, String task, Boolean sensitive) {
        return new CoachRequest(goal, audience, task, sensitive);
    }

    @ParameterizedTest
    @EnumSource(Goal.class)
    void eachGoalUsesItsOwnPromptAndVerificationStep(Goal goal) {
        CoachResponse response = service.coach(request(goal.apiName(), "my book club", "Plan a potluck", false));

        assertEquals(goal.apiName(), response.goal());
        assertTrue(response.suggestedPrompt().startsWith(goal.opening("my book club")));
        assertTrue(response.suggestedPrompt().contains("Task: Plan a potluck"));
        assertTrue(response.suggestedPrompt().contains(goal.instructions()));
        assertEquals(goal.verificationStep(), response.verificationStep());
    }

    @Test
    void verificationStepsAreDifferentForEveryGoal() {
        long distinct = List.of(Goal.values()).stream().map(Goal::verificationStep).distinct().count();
        assertEquals(Goal.values().length, distinct);
    }

    @Test
    void checklistHasAllFivePauseRulesInOrder() {
        CoachResponse response = service.coach(request("draft", "my team", "A thank-you note", false));

        List<PauseRule> checklist = response.pauseChecklist();
        assertEquals(List.of("P", "A", "U", "S", "E"), checklist.stream().map(PauseRule::letter).toList());
        assertEquals("Protect what's private", checklist.get(0).title());
        assertEquals("Assume it's a draft, not an authority", checklist.get(1).title());
        assertEquals("Use a real source", checklist.get(2).title());
        assertEquals("Stop when rushed", checklist.get(3).title());
        assertEquals("Enlist help when in doubt", checklist.get(4).title());
    }

    @Test
    void goalIsCaseInsensitive() {
        assertEquals("explain", service.coach(request("  EXPLAIN ", null, "What is a router?", false)).goal());
    }

    @Test
    void taskOfExactly1000CharactersIsAccepted() {
        String task = "a".repeat(CoachService.MAX_TASK_LENGTH);
        CoachResponse response = service.coach(request("summarize", null, task, false));
        assertTrue(response.suggestedPrompt().contains(task));
    }

    @Test
    void taskOver1000CharactersIsRejected() {
        String task = "a".repeat(CoachService.MAX_TASK_LENGTH + 1);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.coach(request("summarize", null, task, false)));
        assertEquals("taskDescription must be 1000 characters or fewer.", e.getMessage());
    }

    @Test
    void emojiCountAsOneCharacterEach() {
        // Each emoji is two Java chars, so this is 2,000 chars but 1,000 real characters.
        String task = "😀".repeat(CoachService.MAX_TASK_LENGTH);
        service.coach(request("draft", null, task, false));
    }

    // The browser page (limits.js) counts the same way, so these pin down the behavior it copies.

    @Test
    void emojiWithSkinToneCountAsTwoCharacters() {
        String thumbsUp = "👍🏽"; // thumbs up + skin tone modifier = 2 code points
        service.coach(request("draft", null, thumbsUp.repeat(CoachService.MAX_TASK_LENGTH / 2), false));
        assertThrows(ValidationException.class, () -> service.coach(
                request("draft", null, thumbsUp.repeat(CoachService.MAX_TASK_LENGTH / 2) + "a", false)));
    }

    @Test
    void familyEmojiCountsAsFiveCharacters() {
        String family = "👨‍👩‍👧"; // man, zero-width joiner, woman, zero-width joiner, girl
        service.coach(request("draft", null, family.repeat(CoachService.MAX_TASK_LENGTH / 5), false));
        assertThrows(ValidationException.class, () -> service.coach(
                request("draft", null, family.repeat(CoachService.MAX_TASK_LENGTH / 5) + "a", false)));
    }

    @Test
    void surroundingWhitespaceDoesNotCountTowardTheLimit() {
        String task = "  \n" + "a".repeat(CoachService.MAX_TASK_LENGTH) + "\t \n";
        service.coach(request("summarize", null, task, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void blankTaskIsRejected(String task) {
        assertThrows(ValidationException.class, () -> service.coach(request("draft", null, task, false)));
    }

    @Test
    void missingTaskIsRejected() {
        assertThrows(ValidationException.class, () -> service.coach(request("draft", null, null, false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "translate", "drafts"})
    void unknownGoalIsRejectedWithTheValidOptions(String goal) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.coach(request(goal, null, "Something", false)));
        assertEquals("goal must be one of: draft, summarize, organize, explain.", e.getMessage());
    }

    @Test
    void missingGoalIsRejected() {
        assertThrows(ValidationException.class, () -> service.coach(request(null, null, "Something", false)));
    }

    @Test
    void blankAudienceUsesDefault() {
        CoachResponse response = service.coach(request("explain", "  ", "What is Wi-Fi?", false));
        assertTrue(response.suggestedPrompt().startsWith("Please explain the following for a general reader."));
    }

    @Test
    void audienceOver200CharactersIsRejected() {
        String audience = "x".repeat(CoachService.MAX_AUDIENCE_LENGTH + 1);
        assertThrows(ValidationException.class, () -> service.coach(request("draft", audience, "A note", false)));
    }

    @Test
    void sensitiveTaskAddsPlaceholderGuidanceAndReminder() {
        CoachResponse response = service.coach(request("organize", "my doctor", "My medication list", true));

        assertTrue(response.suggestedPrompt().endsWith("\n\nKeep any placeholders like [NAME], [ADDRESS], or [ACCOUNT #] "
                + "exactly as they are, and don't ask me for the real private details."));
        assertEquals(CoachService.PRIVACY_REMINDER, response.privacyReminder());
    }

    @Test
    void nonSensitiveTaskHasNoReminder() {
        CoachResponse response = service.coach(request("organize", null, "My grocery list", false));

        assertFalse(response.suggestedPrompt().contains("[NAME]"));
        assertNull(response.privacyReminder());
    }

    @Test
    void missingSensitiveFlagMeansNotSensitive() {
        assertNull(service.coach(request("draft", null, "A birthday card", null)).privacyReminder());
    }

    @Test
    void errorMessagesNeverEchoTheTask() {
        String secret = "My SSN is 123-45-6789 ";
        String task = secret.repeat(100);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.coach(request("draft", null, task, true)));
        assertFalse(e.getMessage().contains("123-45-6789"));
    }
}
