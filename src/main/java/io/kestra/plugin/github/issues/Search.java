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
    title = "Search for github issues",
    description = "If no authentication is provided, anonymous connect will be used"
)
@Plugin(
    examples = {
        @Example(
            code = """
                   id: search
                   type: io.kestra.plugin.github.issues.Search
                   oauthToken: your_github_token
                   query: "repo:kestra-io/plugin-github is:open"
                   """
        ),
        @Example(
            code = """
                   id: search
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
        name = "The query contains one or more search keywords and qualifiers",
        description = "Allow you to limit your search to specific areas of GitHub"
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        name = "Searched issues mentions by specified user"
    )
    @PluginProperty(dynamic = true)
    private String mentions;

    @Schema(
        name = "Specifies whether issue is open"
    )
    @PluginProperty
    private Boolean open;

    @Schema(
        name = "Specifies whether issue is closed"
    )
    @PluginProperty
    private Boolean closed;

    @Schema(
        name = "Specifies whether issue is merged"
    )
    @PluginProperty
    private Boolean merged;

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
