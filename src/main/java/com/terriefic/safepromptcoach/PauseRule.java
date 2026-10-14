package com.terriefic.safepromptcoach;

import java.util.List;

/** One item of the PAUSE safety checklist. The wording matches the README. */
public record PauseRule(String letter, String title, String detail) {

    public static final List<PauseRule> CHECKLIST = List.of(
            new PauseRule("P", "Protect what's private",
                    "Don't put personal, financial, or health details, or other people's information, into an AI tool."),
            new PauseRule("A", "Assume it's a draft, not an authority",
                    "AI can sound sure of itself and still be wrong, so treat what it gives you as a starting point."),
            new PauseRule("U", "Use a real source",
                    "Check important facts against a trusted source before you rely on them."),
            new PauseRule("S", "Stop when rushed",
                    "If you feel hurried or pressured, step away before you act on what the AI says."),
            new PauseRule("E", "Enlist help when in doubt",
                    "If something seems off or confusing, ask someone you trust."));
}
