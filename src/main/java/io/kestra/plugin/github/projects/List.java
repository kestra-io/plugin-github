package io.kestra.plugin.github.projects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List items from a GitHub Projects v2 board",
    description = """
        Fetches all items from a GitHub Projects v2 board via the GraphQL API, with optional \
        client-side filtering by project field values, status, and labels. \
        Supports pagination for projects with more than 100 items. \
        The token must have the `read:project` scope in addition to `repo` access.\
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all items from a GitHub project.",
            full = true,
            code = """
                   id: github_projects_list_flow
                   namespace: company.team

                   tasks:
                     - id: list_project_items
                       type: io.kestra.plugin.github.projects.List
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       organization: kestra-io
                       projectNumber: 1
                       fetchType: FETCH
                   """
        ),
        @Example(
            title = "List project items filtered by field values, status, and labels, stored to internal storage.",
            full = true,
            code = """
                   id: github_projects_list_filtered_flow
                   namespace: company.team

                   tasks:
                     - id: list_project_items
                       type: io.kestra.plugin.github.projects.List
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       organization: kestra-io
                       projectNumber: 1
                       fields:
                         Owner: Plugins
                       status:
                         - In Progress
                         - Done
                       labels:
                         - area/plugin
                       fetchType: STORE
                   """
        )
    }
)
public class List extends AbstractGithubSearchTask implements RunnableTask<AbstractGithubSearchTask.Output> {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private static final String PROJECTS_QUERY = """
        query($org: String!, $number: Int!, $cursor: String) {
          organization(login: $org) {
            projectV2(number: $number) {
              items(first: 100, after: $cursor) {
                pageInfo {
                  hasNextPage
                  endCursor
                }
                nodes {
                  fieldValues(first: 50) {
                    nodes {
                      ... on ProjectV2ItemFieldTextValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        text
                      }
                      ... on ProjectV2ItemFieldSingleSelectValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        name
                      }
                      ... on ProjectV2ItemFieldDateValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        date
                      }
                      ... on ProjectV2ItemFieldNumberValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        number
                      }
                      ... on ProjectV2ItemFieldIterationValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        title
                      }
                      ... on ProjectV2ItemFieldUserValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        users(first: 10) { nodes { login } }
                      }
                      ... on ProjectV2ItemFieldRepositoryValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        repository { nameWithOwner }
                      }
                      ... on ProjectV2ItemFieldLabelValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        labels(first: 10) { nodes { name } }
                      }
                      ... on ProjectV2ItemFieldMilestoneValue {
                        field { ... on ProjectV2FieldCommon { name } }
                        milestone { title }
                      }
                    }
                  }
                  content {
                    __typename
                    ... on Issue {
                      number
                      title
                      url
                      createdAt
                      closedAt
                      repository { name }
                      assignees(first: 10) { nodes { login } }
                      labels(first: 10) { nodes { name } }
                    }
                  }
                }
              }
            }
          }
        }
        """;

    @Schema(
        title = "GitHub organization login",
        description = "The GitHub organization that owns the project (e.g. `kestra-io`)."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organization;

    @Schema(
        title = "Project number",
        description = "The numeric identifier of the project board visible in the GitHub URL."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> projectNumber;

    @Schema(
        title = "Project field filters",
        description = """
            When set, only items whose project field values match all the listed entries are returned (case-sensitive). \
            Keys are project field names (e.g. `Owner`, `Stage`); values are the expected field values. \
            An empty map disables this filter.\
            """
    )
    @PluginProperty(group = "processing")
    private Property<Map<String, String>> fields;

    @Schema(
        title = "Status filter",
        description = "When set, only items whose Status field matches one of the listed values are returned. An empty list disables this filter."
    )
    @PluginProperty(group = "processing")
    private Property<java.util.List<String>> status;

    @Schema(
        title = "Label filter",
        description = "When set, only items that have at least one of the listed labels are returned. An empty list disables this filter."
    )
    @PluginProperty(group = "processing")
    private Property<java.util.List<String>> labels;

    @Schema(
        title = "Maximum number of items to return",
        description = """
            Caps the number of items returned after all filters are applied. `0` or negative means no limit.
            Note: this limit is applied after all pages are fetched — it does not reduce the number of API calls or memory usage for large projects.\
            """
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> limit = Property.ofValue(0);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var token = resolveToken(runContext);
        var rOrg = runContext.render(this.organization).as(String.class).orElseThrow();
        var rNumber = runContext.render(this.projectNumber).as(Integer.class).orElseThrow();
        var rEndpoint = runContext.render(getEndpoint()).as(String.class).orElse("https://api.github.com");
        var rLimit = runContext.render(this.limit).as(Integer.class).orElse(0);

        var rFields = this.fields != null ? runContext.render(this.fields).asMap(String.class, String.class) : Map.<String, String>of();
        var rStatus = this.status != null ? runContext.render(this.status).asList(String.class) : java.util.List.<String>of();
        var rLabels = this.labels != null ? runContext.render(this.labels).asList(String.class) : java.util.List.<String>of();

        var graphqlUrl = rEndpoint.stripTrailing().replaceFirst("/+$", "") + "/graphql";
        var allItems = new ArrayList<Map<String, Object>>();

        var httpConfig = HttpConfiguration.builder().allowFailed(Property.ofValue(true)).build();
        try (var client = new HttpClient(runContext, httpConfig)) {
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                var variables = new HashMap<String, Object>();
                variables.put("org", rOrg);
                variables.put("number", rNumber);
                if (cursor != null) {
                    variables.put("cursor", cursor);
                }

                var body = MAPPER.writeValueAsString(Map.of("query", PROJECTS_QUERY, "variables", variables));
                var request = HttpRequest.builder()
                    .uri(URI.create(graphqlUrl))
                    .method("POST")
                    .body(HttpRequest.StringRequestBody.builder().content(body).build())
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

                var response = client.request(request, String.class);
                var statusCode = response.getStatus().getCode();
                if (statusCode != 200) {
                    throw new RuntimeException(
                        "GitHub GraphQL request failed with HTTP %d: %s".formatted(statusCode, response.getBody())
                    );
                }

                var root = MAPPER.readTree(response.getBody());

                var errors = root.path("errors");
                if (!errors.isMissingNode() && errors.isArray() && !errors.isEmpty()) {
                    var firstError = errors.get(0);
                    var errorType = firstError.path("type").asText("");
                    var errorMsg = firstError.path("message").asText("unknown GraphQL error");
                    if ("FORBIDDEN".equals(errorType)) {
                        throw new RuntimeException(
                            "GitHub Projects API access denied — ensure the token has the `read:project` scope. Details: " + errorMsg
                        );
                    }
                    throw new RuntimeException("GitHub GraphQL error: " + errorMsg);
                }

                var orgNode = root.path("data").path("organization");
                if (orgNode.isMissingNode() || orgNode.isNull()) {
                    throw new RuntimeException("Organization '%s' not found or not accessible.".formatted(rOrg));
                }
                var projectNode = orgNode.path("projectV2");
                if (projectNode.isMissingNode() || projectNode.isNull()) {
                    throw new RuntimeException("Project #%d not found in organization '%s'.".formatted(rNumber, rOrg));
                }

                var itemsNode = projectNode.path("items");
                var pageInfo = itemsNode.path("pageInfo");
                hasNextPage = pageInfo.path("hasNextPage").asBoolean(false);
                cursor = hasNextPage ? pageInfo.path("endCursor").asText(null) : null;

                for (var node : itemsNode.path("nodes")) {
                    var content = node.path("content");
                    if (content.isNull() || content.isMissingNode()) {
                        continue;
                    }
                    var typeName = content.path("__typename").asText("");
                    if (!"Issue".equals(typeName)) {
                        continue;
                    }
                    var issueNumber = content.path("number");
                    if (issueNumber.isMissingNode() || issueNumber.isNull()) {
                        continue;
                    }

                    var fieldValues = extractFieldValues(node);
                    var item = new HashMap<String, Object>();

                    item.put("title", content.path("title").asText(null));
                    item.put("number", issueNumber.asInt());
                    item.put("url", content.path("url").asText(null));
                    item.put("repository", content.path("repository").path("name").asText(null));
                    item.put("createdAt", content.path("createdAt").asText(null));
                    item.put("closedAt", content.path("closedAt").asText(null));

                    var assigneesList = new ArrayList<String>();
                    for (var assignee : content.path("assignees").path("nodes")) {
                        assigneesList.add(assignee.path("login").asText());
                    }
                    item.put("assignees", assigneesList);

                    var labelsList = new ArrayList<String>();
                    for (var label : content.path("labels").path("nodes")) {
                        labelsList.add(label.path("name").asText());
                    }
                    item.put("labels", labelsList);

                    item.put("status", fieldValues.getOrDefault("Status", null));
                    fieldValues.forEach(item::putIfAbsent);

                    allItems.add(item);
                }
            }
        }

        var filtered = allItems.stream()
            .filter(item -> {
                for (var entry : rFields.entrySet()) {
                    if (!entry.getValue().equals(item.get(entry.getKey()))) {
                        return false;
                    }
                }
                if (!rStatus.isEmpty()) {
                    var itemStatus = (String) item.get("status");
                    if (!rStatus.contains(itemStatus)) {
                        return false;
                    }
                }
                if (!rLabels.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    var itemLabels = (java.util.List<String>) item.get("labels");
                    if (itemLabels == null || itemLabels.stream().noneMatch(rLabels::contains)) {
                        return false;
                    }
                }
                return true;
            })
            .toList();

        if (!allItems.isEmpty() && filtered.isEmpty() && (!rFields.isEmpty() || !rStatus.isEmpty() || !rLabels.isEmpty())) {
            runContext.logger().warn(
                "All {} fetched items were excluded by the configured filters (fields={}, status={}, labels={}). " +
                "Verify that filter values match the actual project field values (filters are case-sensitive).",
                allItems.size(), rFields, rStatus, rLabels
            );
        } else {
            runContext.logger().info("Fetched {} items, {} passed filters.", allItems.size(), filtered.size());
        }

        var limited = (rLimit > 0 && filtered.size() > rLimit) ? filtered.subList(0, rLimit) : filtered;

        return handleFetch(
            runContext,
            limited,
            item -> item,
            runContext.render(fetchType).as(FetchType.class).orElseThrow()
        );
    }

    private Map<String, String> extractFieldValues(JsonNode itemNode) {
        var result = new HashMap<String, String>();
        for (var fv : itemNode.path("fieldValues").path("nodes")) {
            var fieldName = fv.path("field").path("name").asText(null);
            if (fieldName == null) {
                // unknown fragment type — silently skip to stay forward-compatible with new GitHub field types
                continue;
            }
            if (fv.has("text")) {
                result.put(fieldName, fv.path("text").asText(null));
            } else if (fv.has("name")) {
                result.put(fieldName, fv.path("name").asText(null));
            } else if (fv.has("date")) {
                result.put(fieldName, fv.path("date").asText(null));
            } else if (fv.has("number")) {
                result.put(fieldName, fv.path("number").asText(null));
            } else if (fv.has("title")) {
                result.put(fieldName, fv.path("title").asText(null));
            } else if (fv.has("users")) {
                var logins = new ArrayList<String>();
                for (var user : fv.path("users").path("nodes")) {
                    logins.add(user.path("login").asText());
                }
                result.put(fieldName, String.join(",", logins));
            } else if (fv.has("repository")) {
                result.put(fieldName, fv.path("repository").path("nameWithOwner").asText(null));
            } else if (fv.has("labels")) {
                var names = new ArrayList<String>();
                for (var label : fv.path("labels").path("nodes")) {
                    names.add(label.path("name").asText());
                }
                result.put(fieldName, String.join(",", names));
            } else if (fv.has("milestone")) {
                result.put(fieldName, fv.path("milestone").path("title").asText(null));
            }
        }
        return result;
    }
}
