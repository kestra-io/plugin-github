package io.kestra.plugin.github.topics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GitHub;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search GitHub topics",
    description = "Runs the GitHub topics search API and writes matches to storage. Defaults to ascending order; anonymous access returns limited metadata and cannot see private counts."
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
    };

    @Schema(
        title = "Search keywords and qualifiers",
        description = "Topic search syntax combining keywords with qualifiers."
    )
    private Property<String> query;

    @Schema(
        title = "Curation/feature flags",
        description = """
                      CURATED matches curated topics\n
                      FEATURED matches topics featured on https://github.com/topics/\n
                      NOT_CURATED excludes curated topics\n
                      NOT_FEATURED excludes featured topics
                      """
    )
    private Property<Is> is;

    @Schema(
        title = "Repository count filter",
        description = "Supports `>`, `<`, and range (`..`) qualifiers."
    )
    private Property<String> repositories;

    @Schema(
        title = "Created date filter",
        description = "Supports `>`, `<`, and range (`..`) dates."
    )
    private Property<String> created;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHTopicSearchBuilder searchBuilder = new GHTopicSearchBuilder(gitHub, runContext.render(super.getOauthToken()).as(String.class).orElse(null));

        searchBuilder
            .order(runContext.render(this.order).as(Order.class).orElseThrow().toString());

        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::query);
        runContext.render(this.is).as(Is.class).map(Is::toString).ifPresent(searchBuilder::is);
        runContext.render(this.repositories).as(String.class).ifPresent(searchBuilder::repositories);
        runContext.render(this.created).as(String.class).ifPresent(searchBuilder::created);

        GHTopicSearchBuilder.GHTopicResponse topics = searchBuilder.list();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            topics
                .getItems()
                .forEach(throwConsumer(code -> FileSerde.write(output, code)));

            output.flush();

            return Output
                .builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

    @Data
    public static class GHTopicSearchBuilder {

        public static final String ACCEPT_HEADER = "application/vnd.github+json";

        public static final String API_VERSION_HEADER = "2022-11-28";

        private final GitHub root;

        private final String oauthToken;

        private final List<String> terms = new ArrayList<>();

        private final List<String> parameters = new ArrayList<>();

        private Map<String, Object> items;

        public GHTopicSearchBuilder(GitHub gitHub, String oauthToken) {
            this.root = gitHub;
            this.oauthToken = oauthToken;
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

        public GHTopicSearchBuilder order(String value) {
            parameters.add("order=" + value);
            return this;
        }

        private String getApiUrl() {
            return root.getApiUrl() + "/search/topics";
        }

        private String getUrlWithQuery() {
            String url = getApiUrl() + "?q=" + StringUtils.join(terms, " ");

            if (this.parameters.isEmpty()) {
                url += "&" + StringUtils.join(parameters, "&");
            }

            return url;
        }

        public GHTopicResponse list() throws Exception {
            URL url = new URL(getUrlWithQuery().replaceAll(" ", "+"));

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (this.oauthToken != null) {
                connection.setRequestProperty("Authorization", "token " + oauthToken);
            }

            connection.setRequestProperty("Accept", ACCEPT_HEADER);
            connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION_HEADER);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP GET Request Failed with Error code : " + responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            connection.disconnect();

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response.toString(), GHTopicResponse.class);
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
