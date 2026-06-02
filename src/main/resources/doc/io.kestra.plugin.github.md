# How to use the GitHub plugin

Use the GitHub REST API from Kestra flows to automate issues, pull requests, Actions workflows, and search — distinct from `plugin-git`, which handles git protocol operations like clone and push.

## Authentication

Set `oauthToken` to a GitHub Personal Access Token (PAT) or a GitHub App installation token. Fine-grained PATs are preferred — scope them to the minimum repositories and permissions needed. Store the token in a [secret](https://kestra.io/docs/concepts/secret) and reference it with `{{ secret('GITHUB_TOKEN') }}`.

## Tasks

`actions.RunWorkflow` dispatches a GitHub Actions workflow by name or ID — use it to trigger CI pipelines or automation jobs from a Kestra flow and optionally pass inputs. `issues.Create` and `issues.Comment` automate issue management; `pulls.Create` opens a pull request programmatically after a branch push.

For data retrieval and reporting, the `Search` tasks under `code`, `commits`, `issues`, `pulls`, `repositories`, `users`, and `topics` query the GitHub search API and return results as Kestra outputs for downstream processing.
