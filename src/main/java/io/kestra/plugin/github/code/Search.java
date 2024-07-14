package io.kestra.plugin.github.code;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.GithubConnector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search for github code",
    description = "Requires authentication"
)
@Plugin(
    examples = {
        @Example(
            code = """
                   id: search
                   type: io.kestra.plugin.github.code.Search
                   oauthToken: your_github_token
                   query: "addClass in:file language:js repo:jquery/jquery"
                   """
        ),
        @Example(
            code = """
                   id: search
                   type: io.kestra.plugin.github.code.Search
                   oauthToken: your_github_token
                   query: addClass
                   in: file
                   language: js
                   repository: jquery/jquery
                   """
        )
    }
)
public class Search extends GithubConnector implements RunnableTask<Search.Output> {

    @RequiredArgsConstructor
    public enum Order {
        ASC(GHDirection.ASC),
        DESC(GHDirection.DESC);

        private final GHDirection direction;
    }

    @RequiredArgsConstructor
    public enum Sort {
        BEST_MATCH(GHContentSearchBuilder.Sort.BEST_MATCH),
        INDEXED(GHContentSearchBuilder.Sort.INDEXED);

        private final GHContentSearchBuilder.Sort value;
    };

    @RequiredArgsConstructor
    public enum Fork {
        PARENT_AND_FORKS(GHFork.PARENT_AND_FORKS),
        FORKS_ONLY(GHFork.FORKS_ONLY),
        PARENT_ONLY(GHFork.PARENT_ONLY);

        private final GHFork value;
    }

    @Schema(
        name = "The query contains one or more search keywords and qualifiers",
        description = "Allow you to limit your search to specific areas of GitHub"
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        name = "",
        description = ""
    )
    @PluginProperty(dynamic = true)
    private String repository;

    @Schema(
        name = "Search commits in all repositories owned by a certain user"
    )
    @PluginProperty(dynamic = true)
    private String user;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String in;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String language;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String extension;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private Fork fork;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String filename;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String path;

    @Schema(
        name = ""
    )
    @PluginProperty(dynamic = true)
    private String size;

    @Schema(
        name = "Order output",
        description = """
                      ASC - the results will be in ascending order
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    @PluginProperty
    private Order order = Order.ASC;

    @Schema(
        name = "Sort output",
        description = """
                      BEST_MATCH - the results will be sorted by best match results
                      INDEXED - the results will be sorted by the index
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.BEST_MATCH;

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHContentSearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHContent> codes = searchBuilder.list();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            codes.toList()
                .stream()
                .map(
                    throwFunction(Search::getCodeDetails)
                )
                .forEachOrdered(
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

    private GHContentSearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHContentSearchBuilder searchBuilder = gitHub.searchContent();

        searchBuilder
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository));
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user));
        }

        if (this.in != null) {
            searchBuilder.in(runContext.render(this.in));
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language));
        }

        if (this.extension != null) {
            searchBuilder.extension(runContext.render(this.extension));
        }

        if (this.fork != null) {
            searchBuilder.fork(this.fork.value);
        }

        if (this.filename != null) {
            searchBuilder.filename(runContext.render(this.filename));
        }

        if (this.path != null) {
            searchBuilder.path(runContext.render(this.path));
        }

        if (this.size != null) {
            searchBuilder.size(runContext.render(this.size));
        }
        return searchBuilder;
    }

    private static Map<String, Object> getCodeDetails(GHContent code) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        Optional.ofNullable(code.getOwner()).map(GHRepository::getName).ifPresent(text -> body.put("repository_name", text));
        Optional.ofNullable(code.getOwner()).map(GHRepository::getHtmlUrl).ifPresent(text -> body.put("repository_url", text));

        Optional.ofNullable(code.getName()).ifPresent(text -> body.put("name", text));

        Optional.ofNullable(code.getSha()).ifPresent(text -> body.put("sha", text));

        Optional.ofNullable(code.getTarget()).ifPresent(text -> body.put("target", text));
        Optional.ofNullable(code.getType()).ifPresent(text -> body.put("type", text));
        Optional.ofNullable(code.getDownloadUrl()).ifPresent(text -> body.put("download_url", text));

        Optional.ofNullable(code.getGitUrl()).ifPresent(text -> body.put("git_url", text));

        Optional.ofNullable(code.getEncoding()).ifPresent(text -> body.put("encoding", text));
        Optional.ofNullable(code.getPath()).ifPresent(text -> body.put("path", text));

        Optional.ofNullable(code.getHtmlUrl()).ifPresent(text -> body.put("url", text));

        return body;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

}
