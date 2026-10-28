package com.terriefic.safepromptcoach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the browser page's files for the project's privacy and no-outside-calls rules. */
class PageFilesTest {

    private static String read(String file) throws Exception {
        try (InputStream in = PageFilesTest.class.getResourceAsStream("/public/" + file)) {
            assertNotNull(in, file + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"index.html", "styles.css", "limits.js", "app.js"})
    void noOutsideUrls(String file) throws Exception {
        String content = read(file);
        for (String forbidden : new String[] {"http://", "https://", "\"//", "'//", "(//", "@import"}) {
            assertFalse(content.contains(forbidden), file + " contains " + forbidden);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"limits.js", "app.js"})
    void scriptsDoNotLogSaveOrInjectHtml(String file) throws Exception {
        String content = read(file);
        for (String forbidden : new String[] {
                "localStorage", "sessionStorage", "indexedDB", "document.cookie", "console.",
                "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval("}) {
            assertFalse(content.contains(forbidden), file + " contains " + forbidden);
        }
    }

    @Test
    void appSendsExactlyTheApiFieldNames() throws Exception {
        String app = read("app.js");
        for (var component : CoachRequest.class.getRecordComponents()) {
            assertTrue(app.contains(component.getName() + ": "), "app.js does not send " + component.getName());
        }
    }

    @Test
    void pageLimitsMatchTheServer() throws Exception {
        String limits = read("limits.js");
        assertEquals(CoachService.MAX_AUDIENCE_LENGTH, limitFor(limits, "audience"));
        assertEquals(CoachService.MAX_TASK_LENGTH, limitFor(limits, "taskDescription"));
    }

    private static int limitFor(String limits, String field) {
        Matcher m = Pattern.compile("\\b" + field + ":\\s*(\\d+)").matcher(limits);
        assertTrue(m.find(), "limits.js has no limit for " + field);
        return Integer.parseInt(m.group(1));
    }
}
