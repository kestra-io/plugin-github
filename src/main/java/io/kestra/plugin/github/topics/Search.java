package io.kestra.plugin.github.topics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GitHub;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search topics",
    description = "Runs a GitHub topic search and writes matching topic metadata to Kestra internal storage. The task calls the REST API directly so it can honor custom endpoints and token types, and defaults to ascending order."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for topics.",
            full = true,
            code = """
                   id: github_topic_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_topics
                       type: io.kestra.plugin.github.topics.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: "micronaut framework is:not-curated repositories:>100"
                   """
        ),
        @Example(
            title = "Search for topics with conditions.",
            full = true,
            code = """
                   id: github_topic_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_topics
                       type: io.kestra.plugin.github.topics.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: "micronaut framework"
                       is: NOT_CURATED
                       repositories: >100
                   """
        )
    }
)
public class Search extends AbstractGithubTask implements RunnableTask<Search.Output> {
    public enum Order {
        ASC(),
        DESC();

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
        }
    }

    public enum Is {
        CURATED(),
        FEATURED(),
        NOT_CURATED(),
        NOT_FEATURED();

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
        }
    }

    @Schema(
        title = "Search keywords and qualifiers",
        description = "GitHub topic search syntax combining keywords with qualifiers. Note: GitHub's topic search API does not support sorting - results are always returned in best match order."
    )
    private Property<String> query;

    @Schema(
        title = "Curation/feature flags",
        description = """
                      CURATED matches curated topics

                      FEATURED matches topics featured on https://github.com/topics/

                      NOT_CURATED excludes curated topics

                      NOT_FEATURED excludes featured topics
                      """
    )
    private Property<Is> is;

    @Schema(
        title = "Repository count filter",
        description = "Supports `>`, `<`, and range (`..`) qualifiers"
    )
    private Property<String> repositories;

    @Schema(
        title = "Created date filter",
        description = "Supports `>`, `<`, and range (`..`) date syntax"
    )
    private Property<String> created;

    @Deprecated
    @Schema(
        title = "Sort direction",
        description = "DEPRECATED: GitHub's topic search API does not support sorting. This property will be removed in a future version."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);
        var searchBuilder = new GHTopicSearchBuilder(gitHub, runContext, resolveAuthorizationHeader(runContext));

        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::query);
        runContext.render(this.is).as(Is.class).map(Is::toString).ifPresent(searchBuilder::is);
        runContext.render(this.repositories).as(String.class).ifPresent(searchBuilder::repositories);
        runContext.render(this.created).as(String.class).ifPresent(searchBuilder::created);

        var topics = searchBuilder.list();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (var output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            topics.items.forEach(throwConsumer(topic -> FileSerde.write(output, topic)));
            output.flush();

            return new Output(
                runContext.storage().putFile(tempFile)
            );
        }
    }

    private String resolveAuthorizationHeader(RunContext runContext) throws Exception {
        var rAppInstallationToken = runContext.render(this.appInstallationTokenProperty()).as(String.class);
        if (rAppInstallationToken.isPresent()) {
            return "token " + rAppInstallationToken.orElseThrow();
        }

        var rJwtToken = runContext.render(this.jwtTokenProperty()).as(String.class);
        if (rJwtToken.isPresent()) {
            return "Bearer " + rJwtToken.orElseThrow();
        }

        return runContext.render(this.oauthTokenProperty()).as(String.class)
            .map(token -> "token " + token)
            .orElse(null);
    }

    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Output file URI",
            description = "URI of the file written to Kestra internal storage, typically using the `kestra://` scheme"
        )
        private URI uri;

        public Output(URI uri) {
            this.uri = uri;
        }
    }

    public static class GHTopicSearchBuilder {
        private static final String ACCEPT_HEADER = "application/vnd.github+json";
        private static final String API_VERSION_HEADER = "2022-11-28";

        private final GitHub root;
        private final RunContext runContext;
        private final String authorizationHeader;
        private final List<String> terms = new ArrayList<>();
        private final List<String> parameters = new ArrayList<>();

        public GHTopicSearchBuilder(GitHub gitHub, RunContext runContext, String authorizationHeader) {
            this.root = gitHub;
            this.runContext = runContext;
            this.authorizationHeader = authorizationHeader;
        }

        public GHTopicSearchBuilder query(String query) {
            terms.add(query);
            return this;
        }

        public GHTopicSearchBuilder is(String value) {
            return query("is:" + value);
        }

        public GHTopicSearchBuilder repositories(String value) {
            return query("repositories:" + value);
        }

        public GHTopicSearchBuilder created(String value) {
            return query("created:" + value);
        }

        private String getApiUrl() {
            return root.getApiUrl() + "/search/topics";
        }

        private String getUrlWithQuery() {
            var url = new StringBuilder(getApiUrl())
                .append("?q=")
                .append(encode(StringUtils.join(terms, " ")));

            if (!this.parameters.isEmpty()) {
                url.append("&").append(StringUtils.join(parameters, "&"));
            }

            return url.toString();
        }

        private String encode(String value) {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        public GHTopicResponse list() throws Exception {
            var urlWithQuery = getUrlWithQuery();
            var requestBuilder = HttpRequest.builder()
                .uri(URI.create(urlWithQuery))
                .method("GET")
                .addHeader("Accept", ACCEPT_HEADER)
                .addHeader("X-GitHub-Api-Version", API_VERSION_HEADER);

            if (this.authorizationHeader != null) {
                requestBuilder.addHeader("Authorization", this.authorizationHeader);
            }

            try (var client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
                var response = client.request(requestBuilder.build(), String.class);
                if (response.getStatus().getCode() != 200) {
                    throw new IllegalStateException("GitHub topic search failed with status code: " + response.getStatus().getCode());
                }

                return JacksonMapper.ofJson().readValue(response.getBody(), GHTopicResponse.class);
            }
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class GHTopicResponse {
            @JsonProperty("total_count")
            private int totalCount;

            @JsonProperty("incomplete_results")
            private boolean incompleteResults;

            @JsonProperty("items")
            private List<GHTopic> items;

            @JsonProperty("url")
            private URL htmlUrl;

            @Data
            public static class GHTopic {
                private String name;

                @JsonProperty("display_name")
                private String displayName;

                @JsonProperty("short_description")
                private String shortDescription;

                private String description;

                @JsonProperty("created_by")
                private String createdBy;

                private String released;

                @JsonProperty("created_at")
                private String createdAt;

                @JsonProperty("updated_at")
                private String updatedAt;

                private boolean featured;
                private boolean curated;
                private int score;
            }
        }
    }
}
