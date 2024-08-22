package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
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
    title = "Search for GitHub issues",
    description = "If no authentication is provided, anonymous authentication will be used"
)
@Plugin(
    examples = {
        @Example(
            title = "Search for issues in a repository.",
            full = true,
            code = """
                   id: github_issue_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_issues
                       type: io.kestra.plugin.github.issues.Search
                       oauthToken: your_github_token
                       query: "repo:kestra-io/plugin-github is:open"
                   """
        ),
        @Example(
            title = "Search for open issues in a repository.",
            code = """
                   id: github_issue_search_flow
                   namespace: company.team
                   
                   tasks:
                     - id: search_open_issues
                       type: io.kestra.plugin.github.issues.Search
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
        CREATED(GHIssueSearchBuilder.Sort.CREATED),
        UPDATED(GHIssueSearchBuilder.Sort.UPDATED),
        COMMENTS(GHIssueSearchBuilder.Sort.COMMENTS);

        private final GHIssueSearchBuilder.Sort value;
    }

    @Schema(
        title = "The query contains one or more search keywords and qualifiers. Allows you to limit your search to specific areas of GitHub."
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        title = "Searched issues mentions by specified user."
    )
    @PluginProperty(dynamic = true)
    private String mentions;

    @Schema(
        title = "Whether the issue is open."
    )
    @PluginProperty
    private Boolean open;

    @Schema(
        title = "Whether issue is closed."
    )
    @PluginProperty
    private Boolean closed;

    @Schema(
        title = "Whether issue is merged."
    )
    @PluginProperty
    private Boolean merged;

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
                      CREATED - Sorts the results of query by the time issue was created (DEFAULT)\n
                      UPDATED - Sorts the results of query by the tome issue was last time updated\n
                      COMMENTS - Sorts the results of query by the number of comments
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.CREATED;

    @Override
    public FileOutput run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssueSearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHIssue> issues = searchBuilder.list();

        return this.run(runContext, issues, gitHub);
    }

    private GHIssueSearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHIssueSearchBuilder searchBuilder = gitHub.searchIssues();

        searchBuilder
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.mentions != null) {
            searchBuilder.mentions(runContext.render(this.mentions));
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
        return searchBuilder;
    }

}
