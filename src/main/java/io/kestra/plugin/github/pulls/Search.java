package io.kestra.plugin.github.pulls;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.GithubSearchTask;
import io.kestra.plugin.github.GHPullRequestSearchBuilderCustom;
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
    title = "Search for GitHub pull requests",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information"
)
@Plugin(
    examples = {
        @Example(
            code = """                   
                   oauthToken: your_github_token
                   query: "repo:kestra-io/plugin-github is:open"
                   """
        ),
        @Example(
            code = """
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
        title = "The query contains one or more search keywords and qualifiers",
        description = "Allow you to limit your search to specific areas of GitHub"
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        title = "Searched issues mentions by specified user"
    )
    @PluginProperty(dynamic = true)
    private String mentions;

    @Schema(
        title = "Specifies whether the pull request is open"
    )
    @PluginProperty
    private Boolean open;

    @Schema(
        title = "Specifies whether the pull request is closed"
    )
    @PluginProperty
    private Boolean closed;

    @Schema(
        title = "Specifies whether the pull request is merged"
    )
    @PluginProperty
    private Boolean merged;

    @Schema(
        title = "Specifies whether the pull request is in draft"
    )
    @PluginProperty
    private Boolean draft;

    @Schema(
        title = "Search pull requests that are assigned to a certain user"
    )
    @PluginProperty
    private String assigned;

    @Schema(
        title = "Search pull requests that have title like specified"
    )
    @PluginProperty
    private String title;

    @Schema(
        title = "Search for code based on when pull request was closed",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results"
    )
    @PluginProperty
    private String closedAt;

    @Schema(
        title = "Search for code based on when the pull request was created",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results"
    )
    @PluginProperty
    private String createdAt;

    @Schema(
        title = "Search for code based on when pull request was updated last time",
        description = "You can use greater than, less than, and range qualifiers (`..` between two dates) to further filter results"
    )
    @PluginProperty
    private String updatedAt;

    @Schema(
        title = "Search for pull requests that contain that SHA",
        description = "The SHA syntax must be at least seven characters"
    )
    @PluginProperty
    private String commit;

    @Schema(
        title = "Search pull requests in a specific repository"
    )
    @PluginProperty
    private String repository;

    @Schema(
        title = "Filter pull requests based on the branch they are merging into"
    )
    @PluginProperty
    private String base;

    @Schema(
        title = "Filter pull requests based on the branch they came from"
    )
    @PluginProperty
    private String head;

    @Schema(
        title = "Specifies whether pull request is created by user who logged in using token",
        description = "Requires authentication"
    )
    @PluginProperty
    private Boolean createdByMe;

    @Schema(
        title = "Finds pull requests created by a certain user or integration account"
    )
    @PluginProperty

    private String author;

    @Schema(
        title = "Order output",
        description = """
                      ASC - the results will be in ascending order
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    @PluginProperty
    private Order order = Order.ASC;

    @Schema(
        title = "Sort output",
        description = """
                      CREATED - Sorts the results of query by the time issue was created (DEFAULT)
                      UPDATED - Sorts the results of query by the tome issue was last time updated
                      COMMENTS - Sorts the results of query by the number of comments
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.CREATED;

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
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.open != null && this.open.equals(Boolean.TRUE)) {
            searchBuilder.isOpen();
        }

        if (this.closed != null && this.closed.equals(Boolean.TRUE)) {
            searchBuilder.isClosed();
        }

        if (this.merged != null && this.merged.equals(Boolean.TRUE)) {
            searchBuilder.isMerged();
        }

        if (this.draft != null && this.draft.equals(Boolean.TRUE)) {
            searchBuilder.isDraft();
        }

        if (this.assigned != null) {
            searchBuilder.assigned(runContext.render(this.assigned));
        }

        if (this.title != null) {
            searchBuilder.titleLike(runContext.render(this.title));
        }

        if (this.closedAt != null) {
            searchBuilder.closed(runContext.render(this.closedAt));
        }

        if (this.createdAt != null) {
            searchBuilder.created(runContext.render(this.createdAt));
        }

        if (this.updatedAt != null) {
            searchBuilder.updated(runContext.render(this.updatedAt));
        }

        if (this.commit != null) {
            searchBuilder.commit(runContext.render(this.commit));
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository));
        }

        if (this.base != null) {
            searchBuilder.base(runContext.render(this.base));
        }

        if (this.head != null) {
            searchBuilder.head(runContext.render(this.head));
        }

        if (this.createdByMe != null && this.createdByMe.equals(Boolean.TRUE)) {
            searchBuilder.createdByMe();
        }

        if (this.author != null) {
            searchBuilder.author(runContext.render(this.author));
        }

        return searchBuilder;
    }

}
