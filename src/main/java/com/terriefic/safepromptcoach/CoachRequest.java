package com.terriefic.safepromptcoach;

/**
 * The JSON body for POST /api/coach.
 *
 * <p>{@code goal} is kept as a plain string so CoachService can give a friendly error for unknown values.
 * {@code sensitive} is a Boolean so a missing value can default to false.
 */
public record CoachRequest(String goal, String audience, String taskDescription, Boolean sensitive) {
}
