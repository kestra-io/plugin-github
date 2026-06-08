package io.kestra.plugin.github.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GitHub;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            title = "Create an issue and set custom Issue Field values using human-readable field names.",
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
                       fields:
                         Customer: "Kestra"
                         Stage: "In review"
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
    @PluginProperty(group = "main")
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
            (API version `2026-03-10`). Only available for organization repositories — personal repositories \
            always return HTTP 404. \
            Keys can be either human-readable field names (e.g. `"Customer"`, `"Stage"`) or field node IDs \
            (e.g. `PVTF_…`). Human-readable names are resolved automatically to node IDs via the GitHub API \
            (`GET /orgs/{org}/issues/field-definitions`). \
            Values are the field values to set. Unsupported value types are passed through as-is; \
            GitHub will return a 422 if the type is invalid. \
            Requires a token with the `project` scope in addition to `issues: write`. \
            When null or empty the REST call is skipped entirely and existing behavior is preserved.\
            """
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> fields;

    private static final String FIELD_VALUES_API_VERSION = "2026-03-10";
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson();

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

        var rFields = this.fields != null
            ? runContext.render(this.fields).asMap(String.class, Object.class)
            : Map.<String, Object>of();

        if (!rFields.isEmpty()) {
            setFields(runContext, issue, rFields);
        }

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .issueNumber(issue.getNumber())
            .build();
    }

    private void setFields(RunContext runContext, GHIssue issue, Map<String, Object> fields) throws Exception {
        var token = resolveToken(runContext);
        var rEndpoint = runContext.render(getEndpoint()).as(String.class)
            .orElse("https://api.github.com");
        var rRepository = runContext.render(this.repository).as(String.class).orElseThrow();
        var org = rRepository.split("/")[0];

        var resolvedFields = resolveFieldKeys(runContext, fields, token, rEndpoint, org);

        var url = "%s/repos/%s/issues/%d/field-values".formatted(
            rEndpoint.stripTrailing(),
            rRepository,
            issue.getNumber()
        );
        var bodyJson = OBJECT_MAPPER.writeValueAsString(Map.of("field_values", resolvedFields));

        var httpConfig = HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build();
        try (var client = new HttpClient(runContext, httpConfig)) {
            var request = HttpRequest.builder()
                .uri(URI.create(url))
                .method("PUT")
                .body(HttpRequest.StringRequestBody.builder().content(bodyJson).build())
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", FIELD_VALUES_API_VERSION)
                .build();
            var response = client.request(request, String.class);
            var status = response.getStatus().getCode();
            if (status < 200 || status >= 300) {
                var body = response.getBody();
                runContext.logger().error("Failed to set field values on issue {} (HTTP {}): {}",
                    issue.getHtmlUrl(), status,
                    body != null && body.length() > 500 ? body.substring(0, 500) + "…" : body);
                throw new RuntimeException(
                    "Failed to set field values on issue %s (HTTP %d) — see execution logs for details"
                        .formatted(issue.getHtmlUrl(), status)
                );
            }
        }
    }

    private Map<String, Object> resolveFieldKeys(
            RunContext runContext,
            Map<String, Object> fields,
            String token,
            String endpoint,
            String org) throws Exception {

        var needsResolution = fields.keySet().stream().anyMatch(k -> !looksLikeNodeId(k));
        if (!needsResolution) {
            return fields;
        }

        var fieldDefs = fetchFieldDefinitions(runContext, token, endpoint, org);
        var resolved = new HashMap<String, Object>(fields.size());
        for (var entry : fields.entrySet()) {
            var key = entry.getKey();
            if (looksLikeNodeId(key)) {
                resolved.put(key, entry.getValue());
            } else {
                var nodeId = fieldDefs.get(key);
                if (nodeId == null) {
                    var available = String.join(", ", fieldDefs.keySet());
                    throw new IllegalArgumentException(
                        "Unknown field name '%s' in organization '%s'. Available fields: [%s].".formatted(key, org, available)
                    );
                }
                resolved.put(nodeId, entry.getValue());
            }
        }
        return resolved;
    }

    private static boolean looksLikeNodeId(String key) {
        // node IDs are >20 chars of [A-Za-z0-9_-]
        return key.length() > 20 && key.chars().allMatch(c ->
            (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '_' || c == '-'
        );
    }

    private Map<String, String> fetchFieldDefinitions(
            RunContext runContext,
            String token,
            String endpoint,
            String org) throws Exception {

        var httpConfig = HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build();
        try (var client = new HttpClient(runContext, httpConfig)) {
            var restUrl = "%s/orgs/%s/issues/field-definitions".formatted(endpoint.stripTrailing(), org);
            var restRequest = HttpRequest.builder()
                .uri(URI.create(restUrl))
                .method("GET")
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", FIELD_VALUES_API_VERSION)
                .build();
            var restResponse = client.request(restRequest, String.class);
            var restStatus = restResponse.getStatus().getCode();

            if (restStatus == 200) {
                return parseRestFieldDefinitions(restResponse.getBody());
            }

            if (restStatus == 404) {
                runContext.logger().debug(
                    "Field definitions REST endpoint not available for org '{}', trying GraphQL fallback", org);
                return fetchFieldDefinitionsViaGraphQL(runContext, client, token, endpoint, org);
            }

            throw new IllegalArgumentException(
                "Cannot resolve field name: field definitions endpoint returned HTTP %d for organization '%s'. Use the field node ID directly (e.g. PVTF_…)."
                    .formatted(restStatus, org)
            );
        }
    }

    private Map<String, String> parseRestFieldDefinitions(String body) throws Exception {
        var root = OBJECT_MAPPER.readTree(body);
        var result = new HashMap<String, String>();
        var items = root.isArray() ? root : root.path("field_definitions");
        for (var node : items) {
            var name = node.path("name").asText(null);
            var id = node.path("id").asText(null);
            if (name != null && id != null) {
                result.put(name, id);
            }
        }
        return result;
    }

    private Map<String, String> fetchFieldDefinitionsViaGraphQL(
            RunContext runContext,
            HttpClient client,
            String token,
            String endpoint,
            String org) throws Exception {

        var graphqlUrl = endpoint.stripTrailing() + "/graphql";
        var query = OBJECT_MAPPER.writeValueAsString(Map.of(
            "query", "query($org: String!) { organization(login: $org) { issueTypes { nodes { id name } } } }",
            "variables", Map.of("org", org)
        ));
        var graphqlRequest = HttpRequest.builder()
            .uri(URI.create(graphqlUrl))
            .method("POST")
            .body(HttpRequest.StringRequestBody.builder().content(query).build())
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build();
        var graphqlResponse = client.request(graphqlRequest, String.class);
        var graphqlStatus = graphqlResponse.getStatus().getCode();

        if (graphqlStatus == 404) {
            throw new IllegalArgumentException(
                "Cannot resolve field name: field definitions are not available for organization '%s'. Use the field node ID directly (e.g. PVTF_…).".formatted(org)
            );
        }

        if (graphqlStatus != 200) {
            runContext.logger().error("GraphQL field-definitions lookup returned HTTP {}: {}",
                graphqlStatus, graphqlResponse.getBody());
            throw new IllegalArgumentException(
                "GraphQL endpoint returned HTTP " + graphqlStatus + " — see execution logs"
            );
        }

        var root = OBJECT_MAPPER.readTree(graphqlResponse.getBody());
        var nodes = root.path("data").path("organization").path("issueTypes").path("nodes");
        var result = new HashMap<String, String>();
        for (var node : nodes) {
            var name = node.path("name").asText(null);
            var id = node.path("id").asText(null);
            if (name != null && id != null) {
                result.put(name, id);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot resolve field name: field definitions are not available for organization '%s'. Use the field node ID directly (e.g. PVTF_…).".formatted(org)
            );
        }
        return result;
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
