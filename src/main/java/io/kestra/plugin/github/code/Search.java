package io.kestra.plugin.github.code;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Search GitHub code",
    description = "Runs the GitHub code search API and writes matched files to storage. Requires OAuth/JWT auth; defaults to best-match sorting in ascending order."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for code in a repository.",
            full = true,
            code = """
                   id: github_code_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_code
                       type: io.kestra.plugin.github.code.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: "addClass in:file language:js repo:jquery/jquery"
                   """
        ),
        @Example(
            title = "Search for code in a repository.",
            full = true,
            code = """
                   id: github_code_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_code
                       type: io.kestra.plugin.github.code.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
        title = "Search keywords and qualifiers",
        description = "GitHub code search syntax combining keywords with qualifiers like repo, path, language."
    )
    private Property<String> query;

    @Schema(
        title = "Repository to search",
        description = "`owner/repo` used for the `repo:` qualifier."
    )
    private Property<String> repository;

    @Schema(
        title = "User scope",
        description = "Limits search to repositories owned by the given user."
    )
    private Property<String> user;

    @Schema(
        title = "Fields to search",
        description = "Comma-separated `in:` targets (e.g. `file,path`); defaults to all."
    )
    private Property<String> in;

    @Schema(
        title = "Language filter",
        description = "Programming language qualifier."
    )
    private Property<String> language;

    @Schema(
        title = "File extension filter",
        description = "Matches files with the given extension."
    )
    private Property<String> extension;

    @Schema(
        title = "Include forks",
        description = "Controls fork inclusion: parent only, forks only, or both."
    )
    private Property<Fork> fork;

    @Schema(
        title = "File name filter",
        description = "Matches files with this exact name."
    )
    private Property<String> filename;

    @Schema(
        title = "Path prefix filter",
        description = "Limits results to files under this path."
    )
    private Property<String> path;

    @Schema(
        title = "File size filter",
        description = "Supports `>`, `<`, and range syntax (`..`) in bytes."
    )
    private Property<String> size;

    @Schema(
        name = "order",
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        name = "sort",
        title = "Sort field",
        description = "BEST_MATCH ranks by relevance (default); INDEXED sorts by index time."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.BEST_MATCH);

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
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository).as(String.class).orElseThrow());
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user).as(String.class).orElseThrow());
        }

        if (this.in != null) {
            searchBuilder.in(runContext.render(this.in).as(String.class).orElseThrow());
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language).as(String.class).orElseThrow());
        }

        if (this.extension != null) {
            searchBuilder.extension(runContext.render(this.extension).as(String.class).orElseThrow());
        }

        if (this.fork != null) {
            searchBuilder.fork(runContext.render(this.fork).as(Fork.class).orElseThrow().value);
        }

        if (this.filename != null) {
            searchBuilder.filename(runContext.render(this.filename).as(String.class).orElseThrow());
        }

        if (this.path != null) {
            searchBuilder.path(runContext.render(this.path).as(String.class).orElseThrow());
        }

        if (this.size != null) {
            searchBuilder.size(runContext.render(this.size).as(String.class).orElseThrow());
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
