// Sends the form to POST /api/coach and shows the result.
// Privacy: nothing typed here is logged or saved, and returned text is only ever shown as plain text.
"use strict";

const form = document.getElementById("coach-form");
const goalSelect = document.getElementById("goal");
const audienceInput = document.getElementById("audience");
const taskInput = document.getElementById("taskDescription");
const sensitiveCheckbox = document.getElementById("sensitive");
const submitButton = document.getElementById("submit-button");
const formError = document.getElementById("form-error");

const results = document.getElementById("results");
const privacyReminderBox = document.getElementById("privacy-reminder-box");
const privacyReminder = document.getElementById("privacy-reminder");
const promptText = document.getElementById("prompt-text");
const copyButton = document.getElementById("copy-button");
const copyStatus = document.getElementById("copy-status");
const verificationStep = document.getElementById("verification-step");
const pauseList = document.getElementById("pause-list");

const counters = [
    { input: audienceInput, output: document.getElementById("audience-count"), limit: COACH_LIMITS.audience },
    { input: taskInput, output: document.getElementById("task-count"), limit: COACH_LIMITS.taskDescription },
];

// Results go stale as soon as the form changes. formVersion goes up on every edit and
// latestRequest on every submission, so a response or copy that finishes afterward can tell
// it is outdated and is ignored instead of shown.
let formVersion = 0;
let latestRequest = 0;

const GENERIC_ERROR = "Something went wrong. Please try again.";
const NETWORK_ERROR = "Couldn't reach the coach. Check that the app is running, then try again.";

function formatNumber(n) {
    return n.toLocaleString("en-US");
}

function updateCounter({ input, output, limit }) {
    const count = countLikeServer(input.value);
    const over = count - limit;
    output.textContent = over > 0
        ? `${formatNumber(count)} of ${formatNumber(limit)} characters (${formatNumber(over)} over the limit)`
        : `${formatNumber(count)} of ${formatNumber(limit)} characters`;
    output.classList.toggle("over", over > 0);
}

function showFormError(message, field) {
    formError.textContent = message;
    if (field) {
        field.setAttribute("aria-invalid", "true");
        field.focus();
    }
}

function clearFormError() {
    formError.textContent = "";
    for (const field of [goalSelect, audienceInput, taskInput]) {
        field.removeAttribute("aria-invalid");
    }
}

/** Checks the form before sending. Messages describe the problem without repeating what was typed. */
function validate() {
    if (!goalSelect.value) {
        showFormError("Please choose a goal.", goalSelect);
        return false;
    }
    const audienceOver = countLikeServer(audienceInput.value) - COACH_LIMITS.audience;
    if (audienceOver > 0) {
        showFormError(`"Who is it for?" is ${formatNumber(audienceOver)} characters over the `
            + `${formatNumber(COACH_LIMITS.audience)}-character limit.`, audienceInput);
        return false;
    }
    const taskCount = countLikeServer(taskInput.value);
    if (taskCount === 0) {
        showFormError("Please describe your task.", taskInput);
        return false;
    }
    const taskOver = taskCount - COACH_LIMITS.taskDescription;
    if (taskOver > 0) {
        showFormError(`Your task description is ${formatNumber(taskOver)} characters over the `
            + `${formatNumber(COACH_LIMITS.taskDescription)}-character limit.`, taskInput);
        return false;
    }
    return true;
}

function showResults(data) {
    promptText.value = data.suggestedPrompt;
    verificationStep.textContent = data.verificationStep;

    privacyReminder.textContent = data.privacyReminder || "";
    privacyReminderBox.hidden = !data.privacyReminder;

    pauseList.replaceChildren(...data.pauseChecklist.map((rule) => {
        const item = document.createElement("li");
        const title = document.createElement("strong");
        title.textContent = `${rule.letter}: ${rule.title}`;
        const detail = document.createElement("span");
        detail.textContent = rule.detail;
        item.append(title, " ", detail);
        return item;
    }));

    copyStatus.textContent = "";
    copyStatus.className = "message";
    results.hidden = false;
    results.focus();
}

async function submitForm(event) {
    event.preventDefault();
    clearFormError();
    if (!validate()) {
        return;
    }

    const request = ++latestRequest;
    const version = formVersion;
    const isCurrent = () => request === latestRequest && version === formVersion;

    submitButton.disabled = true;
    submitButton.textContent = "Preparing…";
    try {
        const response = await fetch("/api/coach", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                goal: goalSelect.value,
                audience: audienceInput.value,
                taskDescription: taskInput.value,
                sensitive: sensitiveCheckbox.checked,
            }),
        });
        const data = await response.json().catch(() => null);
        if (!isCurrent()) {
            return;
        }
        if (response.ok && data) {
            showResults(data);
        } else {
            results.hidden = true;
            // The server's error messages never include the submitted text, so they are safe to show.
            showFormError(data && typeof data.error === "string" ? data.error : GENERIC_ERROR);
        }
    } catch {
        if (isCurrent()) {
            results.hidden = true;
            showFormError(NETWORK_ERROR);
        }
    } finally {
        if (request === latestRequest) {
            submitButton.disabled = false;
            submitButton.textContent = "Get my safe prompt";
        }
    }
}

/** Copies only the suggested prompt. If that isn't possible, selects it so it can be copied by hand. */
async function copyPrompt() {
    const version = formVersion;
    if (navigator.clipboard && window.isSecureContext) {
        try {
            await navigator.clipboard.writeText(promptText.value);
            if (version !== formVersion) {
                return;
            }
            copyStatus.textContent = "Copied! The prompt is ready to paste into an AI tool.";
            copyStatus.className = "message success";
            return;
        } catch {
            // Fall through to manual copying.
        }
        if (version !== formVersion) {
            return;
        }
    }
    promptText.focus();
    promptText.select();
    copyStatus.textContent = "Couldn't copy automatically. The prompt is now selected: "
        + "press Ctrl+C (or Command+C on a Mac) to copy it.";
    copyStatus.className = "message error";
}

for (const counter of counters) {
    updateCounter(counter);
    counter.input.addEventListener("input", () => updateCounter(counter));
}

/** Any edit makes the shown result outdated: hide it and clear the copy status, without moving focus. */
function discardResults() {
    formVersion += 1;
    results.hidden = true;
    copyStatus.textContent = "";
    copyStatus.className = "message";
}

form.addEventListener("input", discardResults);
form.addEventListener("change", discardResults);
form.addEventListener("submit", submitForm);
copyButton.addEventListener("click", copyPrompt);
