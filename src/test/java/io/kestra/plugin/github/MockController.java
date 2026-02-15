package io.kestra.plugin.github;

import io.micronaut.http.HttpResponse;
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

    @Get("/repos/kestra-io/mock-kestra")
    public HttpResponse<String> repo() {
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
    public HttpResponse<String> workflows() {
        MockController.data = data;
        return HttpResponse.ok("""
            {
              "id": 105842276,
              "path": ".github/workflows/blank.yaml"
            }
            """);
    }

    @Post("/repos/kestra-io/mock-kestra/actions/workflows/105842276/dispatches")
    public HttpResponse<String> dispat(@Body String data) {
        MockController.data = data;
        return HttpResponse.noContent();
    }

    @Post("/repos/kestra-io/mock-kestra/issues")
    public HttpResponse<String> createIssue(@Body String data) {
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
    public HttpResponse<String> getIssue() {
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
    public HttpResponse<String> createIssueComment(@Body String data) {
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
    public HttpResponse<String> createPullRequest(@Body String data) {
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
}
