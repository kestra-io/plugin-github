package io.kestra.plugin.github;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller()
@Consumes("application/json")
@Produces("application/vnd.github+json")
public class MockController {
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
              "type": "User"
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

}
