# Kestra Github Plugin

## What

GitHub plugin for Kestra Exposes 11 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with GitHub, allowing orchestration of GitHub-based operations as part of data pipelines and automation workflows.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `github`

### Key Plugin Classes

- `io.kestra.plugin.github.actions.RunWorkflow`
- `io.kestra.plugin.github.code.Search`
- `io.kestra.plugin.github.commits.Search`
- `io.kestra.plugin.github.issues.Comment`
- `io.kestra.plugin.github.issues.Create`
- `io.kestra.plugin.github.issues.Search`
- `io.kestra.plugin.github.pulls.Create`
- `io.kestra.plugin.github.pulls.Search`
- `io.kestra.plugin.github.repositories.Search`
- `io.kestra.plugin.github.topics.Search`
- `io.kestra.plugin.github.users.Search`

### Project Structure

```
plugin-github/
├── src/main/java/io/kestra/plugin/github/users/
├── src/test/java/io/kestra/plugin/github/users/
├── build.gradle
└── README.md
```

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
