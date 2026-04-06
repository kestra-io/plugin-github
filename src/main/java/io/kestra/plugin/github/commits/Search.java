package io.kestra.plugin.github.commits;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.*;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static io.kestra.core.utils.Rethrow.throwFunction;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search commits",
    description = "Runs a GitHub commit search and writes matching commit metadata to Kestra internal storage. Anonymous execution returns an empty output, and authenticated runs default to `COMMITTER_DATE` sorted in ascending order."
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
public class Search extends AbstractGithubSearchTask implements RunnableTask<AbstractGithubSearchTask.Output> {

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
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(
        title = "Repository to search",
        description = "`owner/repo` value used for the `repo:` qualifier"
    )
    @PluginProperty(group = "destination")
    private Property<String> repository;

    @Schema(
        title = "Repository visibility",
        description = "Value used for the `is:` qualifier such as `public` or `private`"
    )
    @PluginProperty(group = "advanced")
    private Property<String> is;

    @Schema(
        title = "Commit SHA filter",
        description = "Matches commits with the specified SHA-1 hash"
    )
    @PluginProperty(group = "advanced")
    private Property<String> hash;

    @Schema(
        title = "Parent commit SHA",
        description = "Filters by parent commit SHA-1"
    )
    @PluginProperty(group = "advanced")
    private Property<String> parent;

    @Schema(
        title = "Tree SHA filter",
        description = "Filters by Git tree SHA-1"
    )
    @PluginProperty(group = "advanced")
    private Property<String> tree;

    @Schema(
        title = "User scope",
        description = "Limits the search to repositories owned by the given user"
    )
    @PluginProperty(group = "advanced")
    private Property<String> user;

    @Schema(
        title = "Organization scope",
        description = "Limits the search to repositories owned by the given organization"
    )
    @PluginProperty(group = "advanced")
    private Property<String> org;

    @Schema(
        title = "Author login",
        description = "Adds the `author:` qualifier for the GitHub login"
    )
    @PluginProperty(group = "connection")
    private Property<String> author;

    @Schema(
        title = "Author date filter",
        description = "Supports `>`, `<`, and range (`..`) date syntax"
    )
    @PluginProperty(group = "connection")
    private Property<String> authorDate;

    @Schema(
        title = "Author email",
        description = "Filters by author email address"
    )
    @PluginProperty(group = "connection")
    private Property<String> authorEmail;

    @Schema(
        title = "Author name",
        description = "Filters by author display name"
    )
    @PluginProperty(group = "connection")
    private Property<String> authorName;

    @Schema(
        title = "Committer login",
        description = "Adds the `committer:` qualifier for the GitHub login"
    )
    @PluginProperty(group = "advanced")
    private Property<String> committer;

    @Schema(
        title = "Committer date filter",
        description = "Supports `>`, `<`, and range (`..`) date syntax"
    )
    @PluginProperty(group = "advanced")
    private Property<String> committerDate;

    @Schema(
        title = "Committer email",
        description = "Filters by committer email address"
    )
    @PluginProperty(group = "advanced")
    private Property<String> committerEmail;

    @Schema(
        title = "Committer name",
        description = "Filters by committer display name"
    )
    @PluginProperty(group = "advanced")
    private Property<String> committerName;

    @Schema(
        title = "Filter merge commits",
        description = "When set, includes only merge commits for `true` or excludes them for `false`"
    )
    @PluginProperty(group = "advanced")
    private Property<Boolean> merge;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "COMMITTER_DATE sorts by committer timestamp (default); AUTHOR_DATE by author timestamp."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Sort> sort = Property.ofValue(Sort.COMMITTER_DATE);

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        if (gitHub.isAnonymous()) {
            return Output.builder().build();
        }

        GHCommitSearchBuilder searchBuilder = gitHub.searchCommits();

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::q);
        runContext.render(this.repository).as(String.class).ifPresent(searchBuilder::repo);
        runContext.render(this.is).as(String.class).ifPresent(searchBuilder::is);
        runContext.render(this.hash).as(String.class).ifPresent(searchBuilder::hash);
        runContext.render(this.parent).as(String.class).ifPresent(searchBuilder::parent);
        runContext.render(this.tree).as(String.class).ifPresent(searchBuilder::tree);
        runContext.render(this.user).as(String.class).ifPresent(searchBuilder::user);
        runContext.render(this.org).as(String.class).ifPresent(searchBuilder::org);
        runContext.render(this.author).as(String.class).ifPresent(searchBuilder::author);
        runContext.render(this.authorDate).as(String.class).ifPresent(searchBuilder::authorDate);
        runContext.render(this.authorEmail).as(String.class).ifPresent(searchBuilder::authorEmail);
        runContext.render(this.authorName).as(String.class).ifPresent(searchBuilder::authorName);
        runContext.render(this.committer).as(String.class).ifPresent(searchBuilder::committer);
        runContext.render(this.committerDate).as(String.class).ifPresent(searchBuilder::committerDate);
        runContext.render(this.committerEmail).as(String.class).ifPresent(searchBuilder::committerEmail);
        runContext.render(this.committerName).as(String.class).ifPresent(searchBuilder::committerName);
        runContext.render(this.merge).as(Boolean.class).ifPresent(searchBuilder::merge);

        PagedSearchIterable<GHCommit> commits = searchBuilder.list();

        return handleFetch(
            runContext,
            commits.toList(),
            throwFunction(commit -> getCommitDetails(commit, gitHub.isAnonymous())),
            runContext.render(fetchType).as(FetchType.class).orElseThrow()
        );
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
}
