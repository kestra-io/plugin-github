package io.kestra.plugin.github.topics;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.GHTopicSearchBuilder;
import io.kestra.plugin.github.GithubConnector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GitHub;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.Locale;

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
public class Search extends GithubConnector implements RunnableTask<Search.Output> {

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

        GHTopicSearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        GHTopicSearchBuilder.GHTopicResponse topics = searchBuilder.list();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            topics
                .getItems()
                .forEach(
                    throwConsumer(
                        code -> FileSerde.write(output, code)
                    )
                );

            output.flush();

            return Output
                .builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();
        }
    }

    private GHTopicSearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHTopicSearchBuilder searchBuilder = new GHTopicSearchBuilder(gitHub, runContext.render(super.getOauthToken()).as(String.class).orElse(null));

        searchBuilder
            .order(runContext.render(this.order).as(Order.class).orElseThrow().toString());

        if (this.query != null) {
            searchBuilder.query(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (this.is != null) {
            searchBuilder.is(runContext.render(this.is).as(Is.class).orElseThrow().toString());
        }

        if (this.repositories != null) {
            searchBuilder.repositories(runContext.render(this.repositories).as(String.class).orElseThrow());
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created).as(String.class).orElseThrow());
        }
        return searchBuilder;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

}
