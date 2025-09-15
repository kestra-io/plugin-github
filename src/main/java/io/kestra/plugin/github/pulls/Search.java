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
    title = "Search for GitHub pull requests.",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information."
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
                       oauthToken: your_github_token
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
                       oauthToken: your_github_token
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
        title = "The query contains one or more search keywords and qualifiers.",
        description = "Allow you to limit your search to specific areas of GitHub."
    )
    private Property<String> query;

    @Schema(
        title = "Searched issues mentions by specified user."
    )
    private Property<String> mentions;

    @Schema(
        title = "Specifies whether the pull request is open."
    )
    private Property<Boolean> open;

    @Schema(
        title = "Specifies whether the pull request is closed."
    )
    private Property<Boolean> closed;

    @Schema(
        title = "Specifies whether the pull request is merged."
    )
    private Property<Boolean> merged;

    @Schema(
        title = "Specifies whether the pull request is in draft."
    )
    private Property<Boolean> draft;

    @Schema(
        title = "Search pull requests that are assigned to a certain user."
    )
    private Property<String> assigned;

    @Schema(
        title = "Search pull requests that have title like specified."
    )
    private Property<String> title;

    @Schema(
        title = "Search for code based on when pull request was closed.",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results."
    )
    private Property<String> closedAt;

    @Schema(
        title = "Search for code based on when the pull request was created.",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results."
    )
    private Property<String> createdAt;

    @Schema(
        title = "Search for code based on when pull request was updated last time",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results"
    )
    private Property<String> updatedAt;

    @Schema(
        title = "Search for pull requests that contain that SHA.",
        description = "The SHA syntax must be at least seven characters."
    )
    private Property<String> commit;

    @Schema(
        title = "Search pull requests in a specific repository."
    )
    private Property<String> repository;

    @Schema(
        title = "Filter pull requests based on the branch they are merging into."
    )
    private Property<String> base;

    @Schema(
        title = "Filter pull requests based on the branch they came from."
    )
    private Property<String> head;

    @Schema(
        title = "Specifies whether pull request is created by user who logged in using token.",
        description = "Requires authentication."
    )
    private Property<Boolean> createdByMe;

    @Schema(
        title = "Finds pull requests created by a certain user or integration account."
    )
    private Property<String> author;

    @Schema(
        title = "Order of the output.",
        description = """
                      ASC - the results will be in ascending order\n
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort condition for the output.",
        description = """
                      CREATED - Sorts the results of query by the time issue was created (DEFAULT)\n
                      UPDATED - Sorts the results of query by the tome issue was last time updated\n
                      COMMENTS - Sorts the results of query by the number of comments
                      """
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
