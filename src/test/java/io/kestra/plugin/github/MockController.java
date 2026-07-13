package io.kestra.plugin.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller()
@Consumes("application/json")
@Produces("application/vnd.github+json")
public class MockController {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    public static String data;
    public static Map<String, String> headers = new HashMap<>();
    public static Map<String, String> queryParameters = new HashMap<>();

    private void capture(HttpRequest<?> request) {
        headers = new HashMap<>();
        request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));

        queryParameters = new HashMap<>();
        request.getParameters().forEach((name, values) -> queryParameters.put(name, values.getFirst()));
    }

    @Get("/repos/kestra-io/mock-kestra")
    public HttpResponse<String> repo(HttpRequest<?> request) {
        capture(request);
        MockController.data = data;
        return HttpResponse.ok("""
            {
              "id": 1296269,
              "node_id": "MDEwOlJlcG9zaXRvcnkxMjk2MjY5",
              "name": "mock-kestra",
              "full_name": "kestra-io/mock-kestra",
              "owner": {
                  "login": "kestra-io"
              },
              "permissions": {
                  "admin": true,
                  "push": true,
                  "pull": true
              }
            }
            """);
    }

    @Get("/repos/kestra-io/mock-kestra/actions/workflows/105842276")
    public HttpResponse<String> workflows(HttpRequest<?> request) {
        capture(request);
        MockController.data = data;
        return HttpResponse.ok("""
            {
              "id": 105842276,
              "path": ".github/workflows/blank.yaml"
            }
            """);
    }

    @Post("/repos/kestra-io/mock-kestra/actions/workflows/105842276/dispatches")
    public HttpResponse<String> dispat(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;
        return HttpResponse.noContent();
    }

    @Post("/repos/kestra-io/mock-kestra/issues")
    public HttpResponse<String> createIssue(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;
        return HttpResponse.created("""
            {
              "id": 1,
              "number": 42,
              "title": "Test Kestra Github plugin",
              "html_url": "https://github.com/kestra-io/mock-kestra/issues/42",
              "state": "open",
              "user": {
                  "login": "kestra-io"
              }
            }
            """).header("Location", "https://github.com/kestra-io/mock-kestra/issues/42");
    }

    @Get("/repos/kestra-io/mock-kestra/issues/42")
    public HttpResponse<String> getIssue(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "id": 1,
              "number": 42,
              "title": "Test Kestra Github plugin",
              "html_url": "https://github.com/kestra-io/mock-kestra/issues/42",
              "state": "open",
              "user": {
                  "login": "kestra-io"
              }
            }
            """);
    }

    @Get("/orgs/kestra-io/issues/field-definitions")
    public HttpResponse<String> getOrgFieldDefinitions(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            [
              {"id": "PVTF_customer_node_id", "name": "Customer"},
              {"id": "PVTF_stage_node_id", "name": "Stage"}
            ]
            """);
    }

    @Put("/repos/kestra-io/mock-kestra/issues/42/field-values")
    public HttpResponse<String> setIssueFieldValues(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;
        return HttpResponse.ok("{}");
    }

    @Post("/repos/kestra-io/mock-kestra/issues/42/comments")
    public HttpResponse<String> createIssueComment(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;
        return HttpResponse.created("""
            {
              "id": 100,
              "html_url": "https://github.com/kestra-io/mock-kestra/issues/42#issuecomment-100",
              "body": "This comment is a test",
              "user": {
                  "login": "kestra-io"
              }
            }
            """).header("Location", "https://github.com/kestra-io/mock-kestra/issues/42#issuecomment-100");
    }

    @Post("/repos/kestra-io/mock-kestra/pulls")
    public HttpResponse<String> createPullRequest(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;
        return HttpResponse.created("""
            {
              "id": 1,
              "number": 10,
              "title": "Test Kestra Github plugin",
              "html_url": "https://github.com/kestra-io/mock-kestra/pull/10",
              "issue_url": "https://api.github.com/repos/kestra-io/mock-kestra/issues/10",
              "state": "open",
              "head": {
                  "ref": "dev",
                  "label": "kestra-io:dev"
              },
              "base": {
                  "ref": "test",
                  "label": "kestra-io:test"
              },
              "user": {
                  "login": "kestra-io"
              }
            }
            """).header("Location", "https://github.com/kestra-io/mock-kestra/pull/10");
    }

    private String baseUrl(HttpRequest<?> request) {
        var host = request.getHeaders().get("Host");
        return "http://" + host;
    }

    @Get("/search/code")
    public HttpResponse<String> searchCode(HttpRequest<?> request) {
        capture(request);
        var base = baseUrl(request);
        return HttpResponse.ok("""
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "name": "Search.java",
                  "path": "src/main/java/Search.java",
                  "sha": "abc123def456",
                  "url": "%s/repos/kestra-io/plugin-github/contents/src/main/java/Search.java",
                  "git_url": "%s/repos/kestra-io/plugin-github/git/blobs/abc123def456",
                  "html_url": "https://github.com/kestra-io/plugin-github/blob/main/src/main/java/Search.java",
                  "repository": {
                    "id": 1,
                    "name": "plugin-github",
                    "full_name": "kestra-io/plugin-github",
                    "html_url": "https://github.com/kestra-io/plugin-github",
                    "owner": {
                      "login": "kestra-io"
                    }
                  }
                }
              ]
            }
            """.formatted(base, base));
    }

    @Get("/repos/kestra-io/plugin-github/contents/{+path}")
    public HttpResponse<String> getContent(HttpRequest<?> request, @PathVariable String path) {
        capture(request);
        var base = baseUrl(request);
        return HttpResponse.ok("""
            {
              "name": "Search.java",
              "path": "%s",
              "sha": "abc123def456",
              "size": 1024,
              "type": "file",
              "encoding": "base64",
              "content": "",
              "url": "%s/repos/kestra-io/plugin-github/contents/%s",
              "git_url": "%s/repos/kestra-io/plugin-github/git/blobs/abc123def456",
              "html_url": "https://github.com/kestra-io/plugin-github/blob/main/%s",
              "download_url": "https://raw.githubusercontent.com/kestra-io/plugin-github/main/%s"
            }
            """.formatted(path, base, path, base, path, path));
    }

    @Get("/repos/kestra-io/plugin-github")
    public HttpResponse<String> pluginGithubRepo(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "id": 1,
              "name": "plugin-github",
              "full_name": "kestra-io/plugin-github",
              "owner": {"login": "kestra-io"},
              "permissions": {"admin": true, "push": true, "pull": true}
            }
            """);
    }

    @Get("/repos/{owner}/{repo}/statuses/{sha}")
    public HttpResponse<String> getStatuses(HttpRequest<?> request, @PathVariable String owner, @PathVariable String repo, @PathVariable String sha) {
        capture(request);
        return HttpResponse.ok("[]");
    }

    @Get("/repos/{owner}/{repo}/git/trees/{sha}")
    public HttpResponse<String> getTree(HttpRequest<?> request, @PathVariable String owner, @PathVariable String repo, @PathVariable String sha) {
        capture(request);
        var base = baseUrl(request);
        return HttpResponse.ok("""
            {
              "sha": "%s",
              "url": "%s/repos/%s/%s/git/trees/%s",
              "tree": [],
              "truncated": false
            }
            """.formatted(sha, base, owner, repo, sha));
    }

    @Get("/users/{login}")
    public HttpResponse<String> getUser(HttpRequest<?> request, @PathVariable String login) {
        capture(request);
        return HttpResponse.ok("""
            {
              "login": "%s",
              "id": 1,
              "type": "User",
              "name": "Test User",
              "company": "kestra-io",
              "location": "Paris",
              "followers": 10,
              "following": 5,
              "public_repos": 20,
              "created_at": "2020-01-01T00:00:00Z",
              "updated_at": "2024-01-01T00:00:00Z"
            }
            """.formatted(login));
    }

    @Get("/search/commits")
    public HttpResponse<String> searchCommits(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "sha": "abc123def456",
                  "url": "https://api.github.com/repos/kestra-io/plugin-github/commits/abc123def456",
                  "html_url": "https://github.com/kestra-io/plugin-github/commit/abc123def456",
                  "commit": {
                    "message": "Initial commit",
                    "author": {"name": "kestra-bot", "email": "bot@kestra.io", "date": "2024-01-01T00:00:00Z"},
                    "committer": {"name": "kestra-bot", "email": "bot@kestra.io", "date": "2024-01-01T00:00:00Z"},
                    "tree": {"sha": "tree123", "url": "https://api.github.com/repos/kestra-io/plugin-github/git/trees/tree123"}
                  },
                  "author": {"login": "kestra-bot"},
                  "committer": {"login": "kestra-bot"},
                  "repository": {
                    "id": 1,
                    "name": "plugin-github",
                    "full_name": "kestra-io/plugin-github",
                    "owner": {"login": "kestra-io"}
                  }
                }
              ]
            }
            """);
    }

    @Get("/repos/{owner}/{repo}/commits/{sha}")
    public HttpResponse<String> getCommit(HttpRequest<?> request, @PathVariable String owner, @PathVariable String repo, @PathVariable String sha) {
        capture(request);
        var base = baseUrl(request);
        return HttpResponse.ok("""
            {
              "sha": "%s",
              "url": "%s/repos/%s/%s/commits/%s",
              "html_url": "https://github.com/%s/%s/commit/%s",
              "commit": {
                "message": "Initial commit",
                "author": {"name": "kestra-bot", "email": "bot@kestra.io", "date": "2024-01-01T00:00:00Z"},
                "committer": {"name": "kestra-bot", "email": "bot@kestra.io", "date": "2024-01-01T00:00:00Z"},
                "tree": {"sha": "tree123", "url": "%s/repos/%s/%s/git/trees/tree123"}
              },
              "author": {"login": "kestra-bot"},
              "committer": {"login": "kestra-bot"},
              "stats": {"total": 10, "additions": 7, "deletions": 3},
              "files": []
            }
            """.formatted(sha, base, owner, repo, sha, owner, repo, sha, base, owner, repo));
    }

    @Get("/search/topics")
    public HttpResponse<String> searchTopics(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "name": "spring-cloud",
                  "display_name": "Spring Cloud",
                  "short_description": "Distributed systems with Spring",
                  "description": "Spring Cloud tools",
                  "created_by": "vmware",
                  "released": "2024-01-01",
                  "created_at": "2024-01-01T00:00:00Z",
                  "updated_at": "2024-01-02T00:00:00Z",
                  "featured": false,
                  "curated": false,
                  "score": 1
                }
              ]
            }
            """).contentType(MediaType.of("application/json"));
    }

    @Get("/search/issues")
    public HttpResponse<String> searchIssues(HttpRequest<?> request) {
        capture(request);
        var base = baseUrl(request);
        var q = request.getParameters().getFirst("q").orElse("");
        if (q.contains("is:pr")) {
            return HttpResponse.ok("""
                {
                  "total_count": 1,
                  "incomplete_results": false,
                  "items": [
                    {
                      "id": 1, "number": 10, "title": "Test PR", "state": "closed",
                      "url": "%s/repos/kestra-io/plugin-github/pulls/10",
                      "html_url": "https://github.com/kestra-io/plugin-github/pull/10",
                      "pull_request": {"url": "%s/repos/kestra-io/plugin-github/pulls/10"},
                      "user": {"login": "kestra-io", "id": 1, "type": "User"},
                      "assignee": null, "assignees": [], "labels": [], "comments": 0,
                      "created_at": "2024-01-01T00:00:00Z",
                      "closed_at": "2024-01-02T00:00:00Z",
                      "updated_at": "2024-01-02T00:00:00Z",
                      "repository": {
                        "id": 1, "name": "plugin-github", "full_name": "kestra-io/plugin-github",
                        "owner": {"login": "kestra-io"},
                        "html_url": "https://github.com/kestra-io/plugin-github"
                      }
                    }
                  ]
                }
                """.formatted(base, base));
        } else {
            return HttpResponse.ok("""
                {
                  "total_count": 1,
                  "incomplete_results": false,
                  "items": [
                    {
                      "id": 1, "number": 42, "title": "Test Issue", "state": "open",
                      "url": "%s/repos/kestra-io/plugin-github/issues/42",
                      "html_url": "https://github.com/kestra-io/plugin-github/issues/42",
                      "user": {"login": "kestra-io", "id": 1, "type": "User"},
                      "assignee": null, "assignees": [], "labels": [], "comments": 0,
                      "created_at": "2024-01-01T00:00:00Z",
                      "updated_at": "2024-01-01T00:00:00Z",
                      "repository": {
                        "id": 1, "name": "plugin-github", "full_name": "kestra-io/plugin-github",
                        "owner": {"login": "kestra-io"},
                        "html_url": "https://github.com/kestra-io/plugin-github"
                      }
                    }
                  ]
                }
                """.formatted(base));
        }
    }

    @Get("/repos/{owner}/{repo}/pulls/{number}")
    public HttpResponse<String> getPullRequest(HttpRequest<?> request, @PathVariable String owner, @PathVariable String repo, @PathVariable String number) {
        capture(request);
        var base = baseUrl(request);
        return HttpResponse.ok("""
            {
              "id": 1, "number": %s, "title": "Test PR", "state": "closed",
              "url": "%s/repos/%s/%s/pulls/%s",
              "html_url": "https://github.com/%s/%s/pull/%s",
              "user": {"login": "kestra-io", "id": 1, "type": "User"},
              "assignee": null, "assignees": [], "labels": [], "comments": 0,
              "created_at": "2024-01-01T00:00:00Z",
              "closed_at": "2024-01-02T00:00:00Z",
              "updated_at": "2024-01-02T00:00:00Z",
              "requested_reviewers": [],
              "requested_teams": [],
              "head": {"ref": "dev", "label": "kestra-io:dev", "sha": "abc123"},
              "base": {"ref": "main", "label": "kestra-io:main", "sha": "def456"},
              "merged": true
            }
            """.formatted(number, base, owner, repo, number, owner, repo, number));
    }

    @Get("/repos/{owner}/{repo}/pulls/{number}/requested_reviewers")
    public HttpResponse<String> getRequestedReviewers(HttpRequest<?> request, @PathVariable String owner, @PathVariable String repo, @PathVariable String number) {
        capture(request);
        return HttpResponse.ok("""
            {"users": [], "teams": []}
            """);
    }

    @Get("/repos/kestra-io/plugin-github/pulls")
    public HttpResponse<String> pluginGithubPulls(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("[]");
    }

    @Get("/search/repositories")
    public HttpResponse<String> searchRepositories(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "id": 1, "name": "plugin-github", "full_name": "kestra-io/plugin-github",
                  "html_url": "https://github.com/kestra-io/plugin-github",
                  "description": "GitHub plugin for Kestra",
                  "owner": {"login": "kestra-io", "id": 1, "type": "Organization"},
                  "forks_count": 5, "stargazers_count": 100, "open_issues_count": 3,
                  "updated_at": "2024-01-02T00:00:00Z", "created_at": "2024-01-01T00:00:00Z",
                  "language": "Java", "archived": false, "fork": false, "disabled": false,
                  "is_template": false, "default_branch": "main", "visibility": "public",
                  "permissions": {"admin": true, "push": true, "pull": true}
                }
              ]
            }
            """);
    }

    @Post("/graphql")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> graphql(HttpRequest<?> request, @Body String data) {
        capture(request);
        MockController.data = data;

        if (data.contains("projectV2")) {
            return handleProjectsQuery(data);
        }
        return HttpResponse.ok("{\"data\":{}}");
    }

    private HttpResponse<String> handleProjectsQuery(String body) {
        try {
            var root = MAPPER.readTree(body);
            var variables = root.path("variables");
            var org = variables.path("org").asText("");
            var number = variables.path("number").asInt(0);
            var cursor = variables.path("cursor").asText(null);
            var hasCursor = cursor != null && !cursor.isEmpty() && !"null".equals(cursor);

            if ("missing-org".equals(org)) {
                return HttpResponse.ok("{\"data\":{\"organization\":null}}");
            }
            if (number == 999) {
                return HttpResponse.ok("{\"data\":{\"organization\":{\"projectV2\":null}}}");
            }
            if ("forbidden-org".equals(org)) {
                return HttpResponse.ok("""
                    {"errors":[{"type":"FORBIDDEN","message":"Resource not accessible by token"}]}
                    """);
            }

            if (hasCursor && "cursor-abc".equals(cursor)) {
                return HttpResponse.ok("""
                    {
                      "data": {
                        "organization": {
                          "projectV2": {
                            "items": {
                              "pageInfo": {"hasNextPage": false, "endCursor": null},
                              "nodes": [
                                {
                                  "fieldValues": {
                                    "nodes": [
                                      {"field": {"name": "Status"}, "name": "Done"},
                                      {"field": {"name": "Owner"}, "text": "Team B"}
                                    ]
                                  },
                                  "content": {
                                    "__typename": "Issue",
                                    "number": 2,
                                    "title": "Second issue",
                                    "url": "https://github.com/kestra-io/kestra/issues/2",
                                    "createdAt": "2025-02-01T10:00:00Z",
                                    "closedAt": "2025-03-01T08:00:00Z",
                                    "repository": {"name": "kestra"},
                                    "assignees": {"nodes": [{"login": "bob"}]},
                                    "labels": {"nodes": [{"name": "area/core"}]}
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                    """);
            }

            if (number == 2) {
                return HttpResponse.ok("""
                    {
                      "data": {
                        "organization": {
                          "projectV2": {
                            "items": {
                              "pageInfo": {"hasNextPage": true, "endCursor": "cursor-abc"},
                              "nodes": [
                                {
                                  "fieldValues": {
                                    "nodes": [
                                      {"field": {"name": "Status"}, "name": "In Progress"},
                                      {"field": {"name": "Owner"}, "text": "Team A"}
                                    ]
                                  },
                                  "content": {
                                    "__typename": "Issue",
                                    "number": 1,
                                    "title": "First issue",
                                    "url": "https://github.com/kestra-io/kestra/issues/1",
                                    "createdAt": "2025-01-01T10:00:00Z",
                                    "closedAt": null,
                                    "repository": {"name": "kestra"},
                                    "assignees": {"nodes": [{"login": "alice"}]},
                                    "labels": {"nodes": [{"name": "area/plugin"}]}
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                    """);
            }

            return HttpResponse.ok("""
                {
                  "data": {
                    "organization": {
                      "projectV2": {
                        "items": {
                          "pageInfo": {"hasNextPage": false, "endCursor": null},
                          "nodes": [
                            {
                              "fieldValues": {"nodes": []},
                              "content": null
                            },
                            {
                              "fieldValues": {"nodes": []},
                              "content": {"__typename": "PullRequest", "number": 10, "title": "A PR"}
                            },
                            {
                              "fieldValues": {
                                "nodes": [
                                  {"field": {"name": "Status"}, "name": "In Progress"},
                                  {"field": {"name": "Owner"}, "text": "Plugins"},
                                  {"field": {"name": "Release"}, "text": "1.4"}
                                ]
                              },
                              "content": {
                                "__typename": "Issue",
                                "number": 1234,
                                "title": "My task",
                                "url": "https://github.com/kestra-io/kestra/issues/1234",
                                "createdAt": "2025-03-01T10:00:00Z",
                                "closedAt": null,
                                "repository": {"name": "kestra-ee"},
                                "assignees": {"nodes": [{"login": "alice"}]},
                                "labels": {"nodes": [{"name": "area/plugin"}]}
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """);
        } catch (Exception e) {
            return HttpResponse.serverError("{\"errors\":[{\"message\":\"mock error\"}]}");
        }
    }

    @Post("/app/installations/{installationId}/access_tokens")
    public HttpResponse<String> createAppInstallationToken(HttpRequest<?> request, @PathVariable String installationId) {
        capture(request);
        return HttpResponse.created("""
            {
              "token": "ghs_mocktoken_for_installation_%s",
              "expires_at": "2099-12-31T23:59:59Z",
              "permissions": {"issues": "write", "metadata": "read"},
              "repository_selection": "all"
            }
            """.formatted(installationId));
    }

    @Get("/search/users")
    public HttpResponse<String> searchUsers(HttpRequest<?> request) {
        capture(request);
        return HttpResponse.ok("""
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "login": "kestra-io", "id": 1, "type": "Organization",
                  "html_url": "https://github.com/kestra-io"
                }
              ]
            }
            """);
    }

}