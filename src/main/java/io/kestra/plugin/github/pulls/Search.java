package io.kestra.plugin.github.pulls;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.GHPullRequestSearchBuilderCustom;
import io.kestra.plugin.github.GithubSearchTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search GitHub pull requests",
    description = "Runs a GitHub Search API query for pull requests and writes results to storage. Defaults to creation-date ascending; anonymous access omits some fields and can't reach private repositories. Provide an OAuth or JWT token to lift those limits."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for pull requests in a repository.",
            full = true,
            code = """
                   id: github_pulls_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_pull_requests
                       type: io.kestra.plugin.github.pulls.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: "repo:kestra-io/plugin-github is:open"
                   """
        ),
        @Example(
            title = "Search for open pull requests in a repository.",
            full = true,
            code = """
                   id: github_pulls_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_open_pull_requests
                       type: io.kestra.plugin.github.pulls.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/plugin-github
                       open: TRUE
                   """
        )
    }
)
public class Search extends GithubSearchTask implements RunnableTask<GithubSearchTask.FileOutput> {

    @RequiredArgsConstructor
    public enum Order {
        ASC(GHDirection.ASC),
        DESC(GHDirection.DESC);

        private final GHDirection direction;
    }

    @RequiredArgsConstructor
    public enum Sort {
        CREATED(GHPullRequestSearchBuilder.Sort.CREATED),
        UPDATED(GHPullRequestSearchBuilder.Sort.UPDATED),
        COMMENTS(GHPullRequestSearchBuilder.Sort.COMMENTS);

        private final GHPullRequestSearchBuilder.Sort value;
    }

    @Schema(
        title = "Search keywords and qualifiers",
        description = "GitHub pull request search syntax combining keywords with qualifiers like repo, is, label, etc."
    )
    private Property<String> query;

    @Schema(
        title = "Pull requests mentioning this user",
        description = "Matches PRs that mention the given GitHub username."
    )
    private Property<String> mentions;

    @Schema(
        title = "Filter to open pull requests",
        description = "Adds the `is:open` qualifier when true."
    )
    private Property<Boolean> open;

    @Schema(
        title = "Filter to closed pull requests",
        description = "Adds the `is:closed` qualifier when true."
    )
    private Property<Boolean> closed;

    @Schema(
        title = "Filter to merged pull requests",
        description = "Adds the `is:merged` qualifier when true."
    )
    private Property<Boolean> merged;

    @Schema(
        title = "Filter to draft pull requests",
        description = "Adds the `is:draft` qualifier when true."
    )
    private Property<Boolean> draft;

    @Schema(
        title = "Pull requests assigned to user",
        description = "Uses the `assignee:` qualifier for the given username."
    )
    private Property<String> assigned;

    @Schema(
        title = "Title contains text",
        description = "Matches pull requests with titles containing the given text."
    )
    private Property<String> title;

    @Schema(
        title = "Filter by closed date",
        description = "Supports `>`, `<`, and range syntax (`..`) with ISO-8601 dates."
    )
    private Property<String> closedAt;

    @Schema(
        title = "Filter by created date",
        description = "Supports `>`, `<`, and range syntax (`..`) with ISO-8601 dates."
    )
    private Property<String> createdAt;

    @Schema(
        title = "Filter by last update",
        description = "Supports `>`, `<`, and range syntax (`..`) with ISO-8601 dates."
    )
    private Property<String> updatedAt;

    @Schema(
        title = "Filter by commit SHA",
        description = "Requires a commit SHA of at least seven characters."
    )
    private Property<String> commit;

    @Schema(
        title = "Repository to search",
        description = "`owner/repo` value used for the `repo:` qualifier."
    )
    private Property<String> repository;

    @Schema(
        title = "Base branch filter",
        description = "Adds the `base:` qualifier for the target branch name."
    )
    private Property<String> base;

    @Schema(
        title = "Head branch filter",
        description = "Adds the `head:` qualifier for the source branch name."
    )
    private Property<String> head;

    @Schema(
        title = "Only pull requests created by the caller",
        description = "Adds `author:@me`; requires authenticated execution."
    )
    private Property<Boolean> createdByMe;

    @Schema(
        title = "Author username",
        description = "Adds the `author:` qualifier for the given GitHub user or app."
    )
    private Property<String> author;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts oldest first (default); DESC sorts newest first."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "CREATED sorts by creation time (default); UPDATED by last update; COMMENTS by comment count."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.CREATED);

    @Override
    public FileOutput run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHPullRequestSearchBuilderCustom searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHPullRequest> pullRequests = searchBuilder.list();

        return this.run(runContext, pullRequests, gitHub);
    }

    private GHPullRequestSearchBuilderCustom setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHPullRequestSearchBuilderCustom searchBuilder = new GHPullRequestSearchBuilderCustom(gitHub);

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (runContext.render(this.open).as(Boolean.class).orElse(false).equals(Boolean.TRUE)) {
            searchBuilder.isOpen();
        }

        if (runContext.render(this.closed).as(Boolean.class).orElse(false).equals(Boolean.TRUE)) {
            searchBuilder.isClosed();
        }

        if (runContext.render(this.merged).as(Boolean.class).orElse(false).equals(Boolean.TRUE)) {
            searchBuilder.isMerged();
        }

        if (runContext.render(this.draft).as(Boolean.class).orElse(false).equals(Boolean.TRUE)) {
            searchBuilder.isDraft();
        }

        if (this.assigned != null) {
            searchBuilder.assigned(runContext.render(this.assigned).as(String.class).orElseThrow());
        }

        if (this.title != null) {
            searchBuilder.titleLike(runContext.render(this.title).as(String.class).orElseThrow());
        }

        if (this.closedAt != null) {
            searchBuilder.closed(runContext.render(this.closedAt).as(String.class).orElseThrow());
        }

        if (this.createdAt != null) {
            searchBuilder.created(runContext.render(this.createdAt).as(String.class).orElseThrow());
        }

        if (this.updatedAt != null) {
            searchBuilder.updated(runContext.render(this.updatedAt).as(String.class).orElseThrow());
        }

        if (this.commit != null) {
            searchBuilder.commit(runContext.render(this.commit).as(String.class).orElseThrow());
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository).as(String.class).orElseThrow());
        }

        if (this.base != null) {
            searchBuilder.base(runContext.render(this.base).as(String.class).orElseThrow());
        }

        if (this.head != null) {
            searchBuilder.head(runContext.render(this.head).as(String.class).orElseThrow());
        }

        if (runContext.render(this.createdByMe).as(Boolean.class).orElse(false).equals(Boolean.TRUE)) {
            searchBuilder.createdByMe();
        }

        if (this.author != null) {
            searchBuilder.author(runContext.render(this.author).as(String.class).orElseThrow());
        }

        return searchBuilder;
    }

}
