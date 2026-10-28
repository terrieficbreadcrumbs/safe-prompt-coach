// Length limits and character counting that match the server (CoachService.java).
//
// The server trims whitespace from both ends with Java's String.strip(), then counts
// Unicode code points. That means some emoji count as more than one character:
//   😀 = 1    👍🏽 (thumbs up + skin tone) = 2    ❤️ (heart + style marker) = 2
//   🇺🇸 (flag) = 2    👨‍👩‍👧 (family) = 5
// The browser's built-in maxlength counts differently, so the form uses these instead.
"use strict";

const COACH_LIMITS = Object.freeze({
    audience: 200,
    taskDescription: 1000,
});

// Java's Character.isWhitespace: space, line and paragraph separators (except the three
// no-break spaces), plus tab, line feed, vertical tab, form feed, carriage return, and U+001C-U+001F.
const JAVA_WHITESPACE = "(?![\\u00A0\\u2007\\u202F])[\\t\\n\\u000B\\f\\r\\u001C-\\u001F\\p{Zs}\\p{Zl}\\p{Zp}]";
const LEADING_WHITESPACE = new RegExp("^(?:" + JAVA_WHITESPACE + ")+", "u");
const TRAILING_WHITESPACE = new RegExp("(?:" + JAVA_WHITESPACE + ")+$", "u");

/** Trims text the same way Java's String.strip() does. */
function stripLikeServer(text) {
    return text.replace(LEADING_WHITESPACE, "").replace(TRAILING_WHITESPACE, "");
}

/** Counts characters the same way the server does: strip, then count code points. */
function countLikeServer(text) {
    return Array.from(stripLikeServer(text)).length;
}
