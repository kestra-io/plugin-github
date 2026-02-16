package io.kestra.plugin.github.commits;

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
    title = "Search GitHub commits",
    description = "Runs the GitHub commit search API and writes matches to storage. Requires OAuth/JWT auth; anonymous runs return no data. Defaults to committer date sorted ascending."
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
        title = "Search keywords and qualifiers",
        description = "Commit search syntax combining keywords with qualifiers like repo, author, path."
    )
    private Property<String> query;

    @Schema(
        title = "Repository to search",
        description = "`owner/repo` for the `repo:` qualifier."
    )
    private Property<String> repository;

    @Schema(
        title = "Repository visibility",
        description = "Uses the `is:` qualifier (e.g. `public`, `private`)."
    )
    private Property<String> is;

    @Schema(
        title = "Commit SHA filter",
        description = "Matches commits with the specified SHA-1 hash."
    )
    private Property<String> hash;

    @Schema(
        title = "Parent commit SHA",
        description = "Filters by parent commit SHA-1."
    )
    private Property<String> parent;

    @Schema(
        title = "Tree SHA filter",
        description = "Filters by git tree SHA-1."
    )
    private Property<String> tree;

    @Schema(
        title = "User scope",
        description = "Limits search to repositories owned by the given user."
    )
    private Property<String> user;

    @Schema(
        title = "Organization scope",
        description = "Limits search to repositories owned by the given organization."
    )
    private Property<String> org;

    @Schema(
        title = "Author login",
        description = "Adds the `author:` qualifier for the GitHub username."
    )
    private Property<String> author;

    @Schema(
        title = "Author date filter",
        description = "Supports `>`, `<`, and range (`..`) dates."
    )
    private Property<String> authorDate;

    @Schema(
        title = "Author email",
        description = "Filters by author email address."
    )
    private Property<String> authorEmail;

    @Schema(
        title = "Author name",
        description = "Filters by author display name."
    )
    private Property<String> authorName;

    @Schema(
        title = "Committer login",
        description = "Adds the `committer:` qualifier for the GitHub username."
    )
    private Property<String> committer;

    @Schema(
        title = "Committer date filter",
        description = "Supports `>`, `<`, and range (`..`) dates."
    )
    private Property<String> committerDate;

    @Schema(
        title = "Committer email",
        description = "Filters by committer email address."
    )
    private Property<String> committerEmail;

    @Schema(
        title = "Committer name",
        description = "Filters by committer display name."
    )
    private Property<String> committerName;

    @Schema(
        title = "Filter merge commits",
        description = "True to include only merge commits; false to exclude them."
    )
    private Property<Boolean> merge;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "COMMITTER_DATE sorts by committer timestamp (default); AUTHOR_DATE by author timestamp."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.COMMITTER_DATE);

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
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository).as(String.class).orElseThrow());
        }

        if (this.is != null) {
            searchBuilder.is(runContext.render(this.is).as(String.class).orElseThrow());
        }

        if (this.hash != null) {
            searchBuilder.hash(runContext.render(this.hash).as(String.class).orElseThrow());
        }

        if (this.parent != null) {
            searchBuilder.parent(runContext.render(this.parent).as(String.class).orElseThrow());
        }

        if (this.tree != null) {
            searchBuilder.tree(runContext.render(this.tree).as(String.class).orElseThrow());
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user).as(String.class).orElseThrow());
        }

        if (this.org != null) {
            searchBuilder.org(runContext.render(this.org).as(String.class).orElseThrow());
        }

        if (this.author != null) {
            searchBuilder.author(runContext.render(this.author).as(String.class).orElseThrow());
        }

        if (this.authorDate != null) {
            searchBuilder.authorDate(runContext.render(this.authorDate).as(String.class).orElseThrow());
        }

        if (this.authorEmail != null) {
            searchBuilder.authorEmail(runContext.render(this.authorEmail).as(String.class).orElseThrow());
        }

        if (this.authorName != null) {
            searchBuilder.authorName(runContext.render(this.authorName).as(String.class).orElseThrow());
        }

        if (this.committer != null) {
            searchBuilder.committer(runContext.render(this.committer).as(String.class).orElseThrow());
        }

        if (this.committerDate != null) {
            searchBuilder.committerDate(runContext.render(this.committerDate).as(String.class).orElseThrow());
        }

        if (this.committerEmail != null) {
            searchBuilder.committerEmail(runContext.render(this.committerEmail).as(String.class).orElseThrow());
        }

        if (this.committerName != null) {
            searchBuilder.committerName(runContext.render(this.committerName).as(String.class).orElseThrow());
        }

        if (this.merge != null) {
            searchBuilder.merge(runContext.render(this.merge).as(Boolean.class).orElseThrow());
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
