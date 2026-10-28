// A tiny stand-in for the browser, just enough to run the page's limits.js and app.js in tests.
// Requests to the server and clipboard copies stay pending until a test finishes them,
// so tests can control exactly when each one completes.
"use strict";

let activeElement = null;
let focusCount = 0;

class FakeElement {
    constructor(id, tagName, form) {
        this.id = id;
        this.tagName = tagName;
        this.form = form || null;
        this.value = "";
        this.checked = false;
        this.hidden = false;
        this.disabled = false;
        this.textContent = "";
        this.className = "";
        this.children = [];
        this.attributes = {};
        this.listeners = {};
        const element = this;
        this.classList = {
            toggle(name, on) {
                const names = new Set(element.className.split(" ").filter(Boolean));
                if (on) {
                    names.add(name);
                } else {
                    names.delete(name);
                }
                element.className = [...names].join(" ");
            },
        };
    }

    addEventListener(type, listener) {
        (this.listeners[type] = this.listeners[type] || []).push(listener);
    }

    /** Fires an event on this element, then on its form, like a bubbling browser event. */
    dispatch(type) {
        const event = { type, target: this, preventDefault() {} };
        for (const element of this.form && this.form !== this ? [this, this.form] : [this]) {
            for (const listener of element.listeners[type] || []) {
                listener(event);
            }
        }
    }

    setAttribute(name, value) {
        this.attributes[name] = String(value);
    }

    removeAttribute(name) {
        delete this.attributes[name];
    }

    focus() {
        activeElement = this;
        focusCount += 1;
    }

    select() {}

    append(...children) {
        this.children.push(...children);
    }

    replaceChildren(...children) {
        this.children = children;
    }
}

const elements = {};
const fakeForm = new FakeElement("coach-form", "FORM");
fakeForm.form = fakeForm;
elements["coach-form"] = fakeForm;
for (const [id, tagName] of [
    ["goal", "SELECT"], ["audience", "INPUT"], ["taskDescription", "TEXTAREA"], ["sensitive", "INPUT"],
    ["submit-button", "BUTTON"], ["form-error", "P"], ["audience-count", "P"], ["task-count", "P"]]) {
    elements[id] = new FakeElement(id, tagName, fakeForm);
}
for (const [id, tagName] of [
    ["results", "SECTION"], ["privacy-reminder-box", "DIV"], ["privacy-reminder", "P"], ["prompt-text", "TEXTAREA"],
    ["copy-button", "BUTTON"], ["copy-status", "P"], ["verification-step", "P"], ["pause-list", "UL"]]) {
    elements[id] = new FakeElement(id, tagName);
}
elements["results"].hidden = true;
elements["privacy-reminder-box"].hidden = true;

globalThis.window = globalThis;
globalThis.isSecureContext = true;

globalThis.document = {
    getElementById(id) {
        if (!(id in elements)) {
            throw new Error("No element with id " + id);
        }
        return elements[id];
    },
    createElement(tagName) {
        return new FakeElement(null, tagName.toUpperCase());
    },
    get activeElement() {
        return activeElement;
    },
};

// Server requests: each call waits in pendingRequests until a test responds to it or fails it.
const pendingRequests = [];
globalThis.fetch = (url, options) => new Promise((resolve, reject) => {
    pendingRequests.push({
        url,
        body: JSON.parse(options.body),
        respond(status, data) {
            resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(data) });
        },
        fail() {
            reject(new TypeError("Failed to fetch"));
        },
    });
});

// Clipboard copies: each waits in pendingCopies until a test finishes it.
const pendingCopies = [];
globalThis.navigator = {
    clipboard: {
        writeText(text) {
            return new Promise((resolve, reject) => pendingCopies.push({ text, succeed: resolve, fail: reject }));
        },
    },
};

// Helpers for tests.

function el(id) {
    return document.getElementById(id);
}

/** Changes a field the way a visitor would, firing input and change events like a browser. */
function edit(id, value) {
    const field = el(id);
    if (field.tagName === "INPUT" && id === "sensitive") {
        field.checked = value;
    } else {
        field.value = value;
    }
    field.dispatch("input");
    field.dispatch("change");
}

function submit() {
    fakeForm.dispatch("submit");
}

function coachResponse(prompt) {
    return {
        goal: "draft",
        suggestedPrompt: prompt,
        pauseChecklist: [{ letter: "P", title: "Protect what's private", detail: "Detail" }],
        verificationStep: "Check it.",
        privacyReminder: null,
    };
}
