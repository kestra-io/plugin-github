# Kestra Github Plugin

## What

- Provides plugin components under `io.kestra.plugin.github`.
- Includes classes such as `Search`, `Comment`, `Create`, `Search`.

## Why

- What user problem does this solve? Teams need to search GitHub and manage issues, pulls, and workflows from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps GitHub steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on GitHub.

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
