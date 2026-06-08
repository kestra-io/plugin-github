package io.kestra.plugin.github.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GitHub;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an issue",
    description = "Creates a new GitHub issue in the target repository. The authenticated token must be allowed to open issues, and property values are rendered before the issue is created."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an issue in a repository using JWT token.",
            full = true,
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       jwtToken: "{{ secret('GITHUB_JWT_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                   """
        ),
        @Example(
            title = "Create an issue in a repository using OAuth token.",
            full = true,
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       login: your_github_login
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                   """
        ),
        @Example(
            title = "Create an issue in a repository with assignees.",
            full = true,
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                       assignees:
                         - MyDeveloperUserName
                         - MyDesignerUserName
                   """
        ),
        @Example(
            title = "Create an issue and set custom Issue Field values.",
            full = true,
            code = """
                   id: github_issue_create_with_fields_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Automated issue with custom fields
                       body: "Created by Kestra workflow {{ execution.id }}"
                       labels:
                         - automation
                       fieldValues:
                         PVTF_lADOA: "high"
                         PVTF_lADOB: "2024-12-31"
                   """
        )
    }
)
public class Create extends AbstractGithubTask implements RunnableTask<Create.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format where the issue will be created"
    )
    @PluginProperty(group = "destination")
    private Property<String> repository;

    @Schema(
        title = "Issue title",
        description = "Short summary shown in GitHub."
    )
    @PluginProperty(group = "advanced")
    private Property<String> title;

    @Schema(
        title = "Issue body",
        description = "Markdown body for the issue. This value is rendered before the request is sent"
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    @Schema(
        title = "Issue labels",
        description = "Labels to apply to the issue when it is created"
    )
    @PluginProperty(group = "advanced")
    private Property<List<String>> labels;

    @Schema(
        title = "Issue assignees",
        description = "GitHub logins to assign when the issue is created"
    )
    @PluginProperty(group = "advanced")
    private Property<List<String>> assignees;

    @Schema(
        title = "Issue field values",
        description = """
            Custom field values to set on the issue after creation, using the GitHub Issues field-values API \
            (API version `2026-03-10`). The map keys are field node IDs and the values are the field values \
            to set. Unsupported value types are passed through as-is; GitHub will return a 422 if the type \
            is invalid. Requires a token with the `project` scope in addition to `issues: write`. \
            When null or empty the REST call is skipped entirely and existing behavior is preserved.\
            """
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> fieldValues;

    private static final String FIELD_VALUES_API_VERSION = "2026-03-10";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Create.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssueBuilder issueBuilder = gitHub
            .getRepository(runContext.render(this.repository).as(String.class).orElse(null))
            .createIssue(runContext.render(this.title).as(String.class).orElse(null))
            .body(runContext.render(this.body).as(String.class).orElse(null));

        if (this.labels != null) {
            runContext.render(labels).asList(String.class)
                .forEach(issueBuilder::label);
        }

        if (this.assignees != null) {
            runContext.render(assignees).asList(String.class)
                .forEach(issueBuilder::assignee);
        }

        GHIssue issue = issueBuilder.create();

        var rFieldValues = this.fieldValues != null
            ? runContext.render(this.fieldValues).asMap(String.class, Object.class)
            : Map.<String, Object>of();

        if (!rFieldValues.isEmpty()) {
            setFieldValues(runContext, issue, rFieldValues);
        }

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .issueNumber(issue.getNumber())
            .build();
    }

    private void setFieldValues(RunContext runContext, GHIssue issue, Map<String, Object> fieldValues) throws Exception {
        var token = resolveToken(runContext);
        var rEndpoint = runContext.render(getEndpoint()).as(String.class)
            .orElse("https://api.github.com");
        var rRepository = runContext.render(this.repository).as(String.class).orElseThrow();

        var url = "%s/repos/%s/issues/%d/field-values".formatted(
            rEndpoint.stripTrailing(),
            rRepository,
            issue.getNumber()
        );
        var body = OBJECT_MAPPER.writeValueAsString(Map.of("field_values", fieldValues));

        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", FIELD_VALUES_API_VERSION)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                "Failed to set field values on issue %s (HTTP %d): %s"
                    .formatted(issue.getHtmlUrl(), response.statusCode(), response.body())
            );
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Issue URL",
            description = "GitHub URL for the created issue"
        )
        private URL issueUrl;

        @Schema(
            title = "Issue number",
            description = "Numeric issue number assigned by GitHub"
        )
        private Integer issueNumber;
    }

}
