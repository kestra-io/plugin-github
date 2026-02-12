package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.kestra.plugin.github.model.FileOutput;
import io.kestra.plugin.github.services.SearchService;
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
    title = "Search GitHub issues",
    description = "Runs the GitHub issue search API and writes matches to storage. Anonymous access works but omits private repos and some fields; defaults to creation-time ascending."
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/plugin-github
                       open: TRUE
                   """
        )
    }
)
public class Search extends AbstractGithubTask implements RunnableTask<FileOutput> {

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
        title = "Search keywords and qualifiers",
        description = "GitHub issue search syntax combining keywords with qualifiers like repo, is, label."
    )
    private Property<String> query;

    @Schema(
        title = "Issues mentioning user",
        description = "Adds the `mentions:` qualifier for the GitHub username."
    )
    private Property<String> mentions;

    @Schema(
        title = "Filter open issues",
        description = "Adds `is:open` when true."
    )
    private Property<Boolean> open;

    @Schema(
        title = "Filter closed issues",
        description = "Adds `is:closed` when true."
    )
    private Property<Boolean> closed;

    @Schema(
        title = "Filter merged pull requests",
        description = "Adds `is:merged` when true; applies to PRs returned by issue search."
    )
    private Property<Boolean> merged;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "CREATED sorts by creation time (default); UPDATED by last update; COMMENTS by comment count."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.CREATED);

    @Schema(
        title = "Repository filter",
        description = "`owner/repo` appended as `repo:` when provided."
    )
    private Property<String> repository;

    @Override
    public FileOutput run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssueSearchBuilder searchBuilder = gitHub.searchIssues();

        var rQuery = runContext.render(this.query).as(String.class).orElse("");
        var rRepo = runContext.render(this.repository).as(String.class).orElse("");

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        // we append the repository property if provided
        if (!rRepo.isEmpty()) {
            rQuery = (rQuery + " repo:" + rRepo).trim();
        }

        if (!rQuery.isBlank()) {
            searchBuilder.q(rQuery);
        }

        runContext.render(this.mentions).as(String.class).ifPresent(searchBuilder::mentions);
        runContext.render(this.open).as(Boolean.class).filter(r -> r).ifPresent(_ -> searchBuilder.isOpen());
        runContext.render(this.closed).as(Boolean.class).filter(r -> r).ifPresent(_ -> searchBuilder.isClosed());
        runContext.render(this.merged).as(Boolean.class).filter(r -> r).ifPresent(_ -> searchBuilder.isMerged());


        PagedSearchIterable<GHIssue> issues = searchBuilder.list();

        return SearchService.run(runContext, issues, gitHub);
    }
}
