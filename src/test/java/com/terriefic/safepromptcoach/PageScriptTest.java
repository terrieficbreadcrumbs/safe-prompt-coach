package com.terriefic.safepromptcoach;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the browser page's app.js against a fake browser (src/test/resources/fake-browser.js)
 * to check that outdated results never stay on screen.
 */
class PageScriptTest {

    private Context js;

    @BeforeEach
    void loadPage() throws Exception {
        js = Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        load("/fake-browser.js");
        load("/public/limits.js");
        load("/public/app.js");
    }

    @AfterEach
    void closePage() {
        js.close();
    }

    private void load(String resource) throws Exception {
        try (InputStream in = PageScriptTest.class.getResourceAsStream(resource)) {
            String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            js.eval(Source.newBuilder("js", code, resource).build());
        }
    }

    /** Runs JavaScript in the page. Finished promises settle before this returns. */
    private void run(String code) {
        js.eval("js", code);
    }

    private boolean bool(String expression) {
        return js.eval("js", expression).asBoolean();
    }

    private String text(String expression) {
        return js.eval("js", expression).asString();
    }

    private int number(String expression) {
        return js.eval("js", expression).asInt();
    }

    private void submitValidForm() {
        run("el('goal').value = 'draft'; el('taskDescription').value = 'A thank-you note'; submit();");
    }

    /** Submits, gets a result back, and copies it, so there is a visible result and copy status. */
    private void showResultAndCopy() {
        submitValidForm();
        run("pendingRequests.shift().respond(200, coachResponse('First prompt'));");
        run("el('copy-button').dispatch('click'); pendingCopies.shift().succeed();");
        assertFalse(bool("el('results').hidden"));
        assertEquals("Copied! The prompt is ready to paste into an AI tool.", text("el('copy-status').textContent"));
    }

    @Test
    void submittingShowsTheResultAndMovesFocusToIt() {
        submitValidForm();
        assertTrue(bool("el('submit-button').disabled"));

        run("pendingRequests.shift().respond(200, coachResponse('First prompt'));");

        assertFalse(bool("el('results').hidden"));
        assertEquals("First prompt", text("el('prompt-text').value"));
        assertEquals("results", text("document.activeElement.id"));
        assertFalse(bool("el('submit-button').disabled"));
    }

    @ParameterizedTest(name = "editing {0}")
    @CsvSource({
            "goal, \"explain\"",
            "audience, \"my team\"",
            "taskDescription, \"A different task\"",
            "sensitive, true",
    })
    void editingAnyFieldHidesTheResultAndClearsCopyStatusWithoutMovingFocus(String field, String value) {
        showResultAndCopy();
        run("el('" + field + "').focus();");
        int focusCountBefore = number("focusCount");

        run("edit('" + field + "', " + value + ");");

        assertTrue(bool("el('results').hidden"));
        assertEquals("", text("el('copy-status').textContent"));
        assertEquals("message", text("el('copy-status').className"));
        assertEquals(field, text("document.activeElement.id"));
        assertEquals(focusCountBefore, number("focusCount"));
    }

    @Test
    void uncheckingSensitiveAlsoHidesTheResult() {
        run("el('sensitive').checked = true;");
        showResultAndCopy();

        run("edit('sensitive', false);");

        assertTrue(bool("el('results').hidden"));
    }

    @Test
    void responseArrivingAfterAnEditIsNotShown() {
        submitValidForm();
        run("el('taskDescription').focus(); edit('taskDescription', 'A changed task');");
        int focusCountBefore = number("focusCount");

        run("pendingRequests.shift().respond(200, coachResponse('Outdated prompt'));");

        assertTrue(bool("el('results').hidden"));
        assertEquals("", text("el('prompt-text').value"));
        assertEquals(focusCountBefore, number("focusCount"), "an outdated response must not move focus");
        assertFalse(bool("el('submit-button').disabled"));
        assertEquals("Get my safe prompt", text("el('submit-button').textContent"));
    }

    @Test
    void errorArrivingAfterAnEditIsNotShown() {
        submitValidForm();
        run("edit('goal', 'summarize');");

        run("pendingRequests.shift().respond(400, { error: 'taskDescription is required.' });");

        assertEquals("", text("el('form-error').textContent"));
        assertFalse(bool("el('submit-button').disabled"));
    }

    @Test
    void networkFailureAfterAnEditIsNotShown() {
        submitValidForm();
        run("edit('audience', 'my neighbors');");

        run("pendingRequests.shift().fail();");

        assertEquals("", text("el('form-error').textContent"));
        assertFalse(bool("el('submit-button').disabled"));
    }

    @Test
    void olderResponseCannotReplaceANewerOne() {
        submitValidForm();
        run("edit('taskDescription', 'Second task'); submit();");
        assertEquals(2, number("pendingRequests.length"));

        run("pendingRequests[1].respond(200, coachResponse('Second prompt'));");
        run("pendingRequests[0].respond(200, coachResponse('First prompt'));");

        assertFalse(bool("el('results').hidden"));
        assertEquals("Second prompt", text("el('prompt-text').value"));
    }

    @Test
    void onlyTheNewestRequestReEnablesTheButton() {
        submitValidForm();
        run("edit('taskDescription', 'Second task'); submit();");

        run("pendingRequests[0].respond(200, coachResponse('First prompt'));");
        assertTrue(bool("el('submit-button').disabled"), "the newer request is still running");

        run("pendingRequests[1].respond(200, coachResponse('Second prompt'));");
        assertFalse(bool("el('submit-button').disabled"));
    }

    @Test
    void copyFinishingAfterAnEditDoesNotShowAStatus() {
        submitValidForm();
        run("pendingRequests.shift().respond(200, coachResponse('First prompt'));");
        run("el('copy-button').dispatch('click');");

        run("edit('audience', 'my team');");
        run("pendingCopies.shift().succeed();");

        assertEquals("", text("el('copy-status').textContent"));
    }

    @Test
    void failedCopyAfterAnEditDoesNotShowAStatusOrMoveFocus() {
        submitValidForm();
        run("pendingRequests.shift().respond(200, coachResponse('First prompt'));");
        run("el('copy-button').dispatch('click');");
        run("el('audience').focus();");

        run("edit('audience', 'my team');");
        run("pendingCopies.shift().fail(new Error('denied'));");

        assertEquals("", text("el('copy-status').textContent"));
        assertEquals("audience", text("document.activeElement.id"));
    }

    @Test
    void failedCopyWithoutEditsStillSelectsThePromptForManualCopying() {
        submitValidForm();
        run("pendingRequests.shift().respond(200, coachResponse('First prompt'));");

        run("el('copy-button').dispatch('click'); pendingCopies.shift().fail(new Error('denied'));");

        assertEquals("Couldn't copy automatically. The prompt is now selected: "
                + "press Ctrl+C (or Command+C on a Mac) to copy it.", text("el('copy-status').textContent"));
        assertEquals("prompt-text", text("document.activeElement.id"));
    }
}
