# AI Without the Fear: Safe Prompt Coach

*A Terriefic Technology Training project*

> 🚧 **Under construction.** This project is still being built.

## What it does

Safe Prompt Coach helps you write a safe, useful prompt for an AI tool.

1. You pick a task: **draft**, **summarize**, **organize**, or **explain**.
2. The coach helps you write a prompt for that task.
3. It checks your prompt against the PAUSE safety rules.
4. It gives you a step to verify what the AI tells you.

## Who it's for

Adults who are new to AI and a little nervous about it.

## The PAUSE rules

- **P – Protect what's private:** Don't put personal, financial, or health details, or other people's information, into an AI tool.
- **A – Assume it's a draft, not an authority:** AI can sound sure of itself and still be wrong, so treat what it gives you as a starting point.
- **U – Use a real source:** Check important facts against a trusted source before you rely on them.
- **S – Stop when rushed:** If you feel hurried or pressured, step away before you act on what the AI says.
- **E – Enlist help when in doubt:** If something seems off or confusing, ask someone you trust.

## Run it locally

You need Java 25 and Maven.

```sh
mvn test                                # run the tests
mvn compile exec:java                   # start on http://localhost:8080 (set PORT to change it)

mvn package                             # or build target/safe-prompt-coach.jar
java -jar target/safe-prompt-coach.jar  # and run the jar
```

Then open http://localhost:8080/ in your browser to use the coach.

Or try the API directly:

```sh
curl localhost:8080/health
curl -X POST localhost:8080/api/coach -H 'Content-Type: application/json' \
  -d '{"goal":"summarize","audience":"my book club","taskDescription":"Summarize this article about gardening","sensitive":false}'
```

`goal` is one of `draft`, `summarize`, `organize`, or `explain`. `taskDescription` can be up to 1,000 characters. `audience` is optional and can be up to 200 characters. If `sensitive` is `true`, the coach adds placeholder guidance and a privacy reminder.

## About Terriefic Technology Training

Terriefic makes practical AI education and everyday technology guidance easier to understand, with thoughtful support for work, personal life, ethical decision-making, Christian-perspective learning, and navigating devices.

Learn more at [terriefic.com](https://terriefic.com).
