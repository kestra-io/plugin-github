package io.kestra.plugin.github.topics;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.GithubConnector;
import io.kestra.plugin.github.GHTopicSearchBuilder;
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
    title = "Search for GitHub topics.",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information."
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
                       oauthToken: your_github_token
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
                       oauthToken: your_github_token
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
        title = "The query contains one or more search keywords and qualifiers.",
        description = "Allow you to limit your search to specific areas of GitHub."
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        title = "The query contains one or more search keywords and qualifiers.",
        description = """
                      CURATED - Matches topics that are curated\n
                      FEATURED - Matches topics that are featured on `https://github.com/topics/`\n
                      NOT_CURATED - Matches topics that don't have extra information, such as a description or logo\n
                      NOT_FEATURED - Matches topics that aren't featured on `https://github.com/topics/`
                      """
    )
    @PluginProperty(dynamic = true)
    private Is is;

    @Schema(
        title = "Matches topics that have number of repositories.",
        description = "You can use greater than, less than, and range qualifiers to further filter results."
    )
    @PluginProperty(dynamic = true)
    private String repositories;

    @Schema(
        title = "The query contains one or more search keywords and qualifiers.",
        description = "You can use greater than, less than, and range qualifiers to further filter results."
    )
    @PluginProperty(dynamic = true)
    private String created;

    @Schema(
        title = "Order of the output.",
        description = """
                      ASC - the results will be in ascending order\n
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    @PluginProperty
    private Order order = Order.ASC;

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
        GHTopicSearchBuilder searchBuilder = new GHTopicSearchBuilder(gitHub, super.getOauthToken());

        searchBuilder
            .order(this.order.toString());

        if (this.query != null) {
            searchBuilder.query(runContext.render(this.query));
        }

        if (this.is != null) {
            searchBuilder.is(runContext.render(this.is.toString()));
        }

        if (this.repositories != null) {
            searchBuilder.repositories(runContext.render(this.repositories));
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created));
        }
        return searchBuilder;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

}
