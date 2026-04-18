# Kestra Github Plugin

## What

- Provides plugin components under `io.kestra.plugin.github`.
- Includes classes such as `Search`, `Comment`, `Create`, `Search`.

## Why

- This plugin integrates Kestra with GitHub Actions.
- It provides tasks that dispatch and monitor GitHub Actions workflows.

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

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
