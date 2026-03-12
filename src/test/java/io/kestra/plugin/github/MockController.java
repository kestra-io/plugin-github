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
