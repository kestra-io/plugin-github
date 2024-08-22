package io.kestra.plugin.github.commits;

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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search for GitHub commits.",
    description = "Requires authentication."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for commits in a repository.",
            full = true,
            code = """
                   id: github_commit_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_commit
                       type: io.kestra.plugin.github.commits.Search
                       oauthToken: your_github_token
                       query: "Initial repo:kestra-io/plugin-github language:java"
                   """
        ),
        @Example(
            title = "Search for commits in a repository.",
            full = true,
            code = """
                   id: github_commit_search_flow
                   namespace: company.team
                   
                   tasks:
                     - id: search_commit
                       type: io.kestra.plugin.github.commits.Search
                       oauthToken: your_github_token
                       query: Initial
                       repository: kestra-io/plugin-github
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
        COMMITTER_DATE(GHCommitSearchBuilder.Sort.COMMITTER_DATE),
        AUTHOR_DATE(GHCommitSearchBuilder.Sort.AUTHOR_DATE);

        private final GHCommitSearchBuilder.Sort value;
    }

    @Schema(
        title = "The query contains one or more search keywords and qualifiers.",
        description = "Allows you to limit your search to specific areas of GitHub."
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        title = "Search in specified repository."
    )
    @PluginProperty(dynamic = true)
    private String repository;

    @Schema(
        title = "Matches commits from repositories with the specified visibility."
    )
    @PluginProperty(dynamic = true)
    private String is;

    @Schema(
        title = "Matches commits with the specified SHA-1 hash."
    )
    @PluginProperty(dynamic = true)
    private String hash;

    @Schema(
        title = "Matches commits whose parent has the specified SHA-1 hash."
    )
    @PluginProperty(dynamic = true)
    private String parent;

    @Schema(
        title = "Matches commits with the specified SHA-1 git tree hash."
    )
    @PluginProperty(dynamic = true)
    private String tree;

    @Schema(
        title = "Search commits in all repositories owned by a certain user"
    )
    @PluginProperty(dynamic = true)
    private String user;

    @Schema(
        title = "Search commits in all repositories owned by a certain organization."
    )
    @PluginProperty(dynamic = true)
    private String org;

    @Schema(
        title = "Find commits by a particular user."
    )
    @PluginProperty(dynamic = true)
    private String author;

    @Schema(
        title = "Match commits authored within the specified date range. When you search for a date, you can use greater than, less than, and range qualifiers to further filter results."
    )
    @PluginProperty(dynamic = true)
    private String authorDate;

    @Schema(
        title = "Match commits by the author's full email address."
    )
    @PluginProperty(dynamic = true)
    private String authorEmail;

    @Schema(
        title = "Match commits by the name of the author"
    )
    @PluginProperty(dynamic = true)
    private String authorName;

    @Schema(
        title = "Find commits by a particular user"
    )
    @PluginProperty(dynamic = true)
    private String committer;

    @Schema(
        title = "Match commits committed within the specified date range.",
        description = "When you search for a date, you can use greater than, less than, and range qualifiers to further filter results."
    )
    @PluginProperty(dynamic = true)
    private String committerDate;

    @Schema(
        title = "Match commits by the committer's full email address."
    )
    @PluginProperty(dynamic = true)
    private String committerEmail;

    @Schema(
        title = "Match commits by the name of the committer"
    )
    @PluginProperty(dynamic = true)
    private String committerName;

    @Schema(
        title = "Whether to filter merge commits."
    )
    @PluginProperty
    private Boolean merge;

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

    @Schema(
        title = "Sort condition for the output.",
        description = """
                      COMMITTER_DATE - the results will be sorted by when user joined to Github\n
                      AUTHOR_DATE - the results will be sorted by the number of repositories owned by user
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.COMMITTER_DATE;

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        if (gitHub.isAnonymous()) {
            return Output.builder().build();
        }

        GHCommitSearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHCommit> commits = searchBuilder.list();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            commits.toList()
                .stream()
                .map(
                    throwFunction(ghCommit -> getCommitDetails(ghCommit, gitHub.isAnonymous()))
                )
                .forEachOrdered(
                    throwConsumer(
                        user -> FileSerde.write(output, user)
                    )
                );

            output.flush();

            return Output
                .builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();
        }
    }

    private GHCommitSearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHCommitSearchBuilder searchBuilder = gitHub.searchCommits();

        searchBuilder
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository));
        }

        if (this.is != null) {
            searchBuilder.is(runContext.render(this.is));
        }

        if (this.hash != null) {
            searchBuilder.hash(runContext.render(this.hash));
        }

        if (this.parent != null) {
            searchBuilder.parent(runContext.render(this.parent));
        }

        if (this.tree != null) {
            searchBuilder.tree(runContext.render(this.tree));
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user));
        }

        if (this.org != null) {
            searchBuilder.org(runContext.render(this.org));
        }

        if (this.author != null) {
            searchBuilder.author(runContext.render(this.author));
        }

        if (this.authorDate != null) {
            searchBuilder.authorDate(runContext.render(this.authorDate));
        }

        if (this.authorEmail != null) {
            searchBuilder.authorEmail(runContext.render(this.authorEmail));
        }

        if (this.authorName != null) {
            searchBuilder.authorName(runContext.render(this.authorName));
        }

        if (this.committer != null) {
            searchBuilder.committer(runContext.render(this.committer));
        }

        if (this.committerDate != null) {
            searchBuilder.committerDate(runContext.render(this.committerDate));
        }

        if (this.committerEmail != null) {
            searchBuilder.committerEmail(runContext.render(this.committerEmail));
        }

        if (this.committerName != null) {
            searchBuilder.committerName(runContext.render(this.committerName));
        }

        if (this.merge != null) {
            searchBuilder.merge(this.merge);
        }
        return searchBuilder;
    }

    private static Map<String, Object> getCommitDetails(GHCommit commit, boolean isAnonymous) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        Optional
            .ofNullable(commit.getSHA1())
            .ifPresent(text -> body.put("sha", text));

        Optional
            .ofNullable(commit.getAuthor())
            .filter(Predicate.not(o -> isAnonymous))
            .map(GHPerson::getLogin)
            .ifPresent(text -> body.put("author", text));

        Optional
            .ofNullable(commit.getCommitter())
            .map(GHPerson::getLogin)
            .ifPresent(text -> body.put("committer", text));

        Optional
            .ofNullable(commit.getAuthoredDate())
            .map(Date::toString)
            .ifPresent(text -> body.put("authored_date", text));
        Optional
            .ofNullable(commit.getCommitDate())
            .map(Date::toString)
            .ifPresent(text -> body.put("commit_date", text));

        Optional
            .ofNullable(commit.getOwner())
            .map(GHRepository::getName)
            .ifPresent(text -> body.put("repository", text));
        Optional
            .ofNullable(commit.getCommitShortInfo())
            .map(GHCommit.ShortInfo::getMessage)
            .ifPresent(text -> body.put("message", text));

        body.put("lines_changed", commit.getLinesChanged());
        body.put("lines_added", commit.getLinesAdded());
        body.put("lines_deleted", commit.getLinesDeleted());

        Optional
            .ofNullable(commit.getTree())
            .map(GHTree::getSha)
            .ifPresent(text -> body.put("tree_sha", text));
        Optional
            .ofNullable(commit.getTree())
            .map(GHTree::getUrl)
            .ifPresent(text -> body.put("tree_url", text));

        Optional
            .ofNullable(commit.getLastStatus())
            .map(GHCommitStatus::getContext)
            .ifPresent(text -> body.put("last_status", text));

        Optional
            .ofNullable(commit.getHtmlUrl())
            .ifPresent(text -> body.put("url", text));

        return body;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

}
