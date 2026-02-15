package io.kestra.plugin.github.pulls;

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
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.*;

import java.util.ArrayList;
import java.util.List;

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
public class Search extends AbstractGithubTask implements RunnableTask<FileOutput> {

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

        GHPullRequestSearchBuilderCustom searchBuilder = new GHPullRequestSearchBuilderCustom(gitHub);

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::q);
        runContext.render(this.open).as(Boolean.class).filter(b -> b).ifPresent(ignored -> searchBuilder.isOpen());
        runContext.render(this.closed).as(Boolean.class).filter(b -> b).ifPresent(ignored -> searchBuilder.isClosed());
        runContext.render(this.merged).as(Boolean.class).filter(b -> b).ifPresent(ignored -> searchBuilder.isMerged());
        runContext.render(this.draft).as(Boolean.class).filter(b -> b).ifPresent(ignored -> searchBuilder.isDraft());
        runContext.render(this.draft).as(Boolean.class).filter(b -> b).ifPresent(ignored -> searchBuilder.isDraft());
        runContext.render(this.assigned).as(String.class).ifPresent(searchBuilder::assigned);
        runContext.render(this.title).as(String.class).ifPresent(searchBuilder::titleLike);
        runContext.render(this.closedAt).as(String.class).ifPresent(searchBuilder::closed);
        runContext.render(this.createdAt).as(String.class).ifPresent(searchBuilder::created);
        runContext.render(this.updatedAt).as(String.class).ifPresent(searchBuilder::updated);
        runContext.render(this.commit).as(String.class).ifPresent(searchBuilder::commit);
        runContext.render(this.repository).as(String.class).ifPresent(searchBuilder::repo);
        runContext.render(this.base).as(String.class).ifPresent(searchBuilder::base);
        runContext.render(this.head).as(String.class).ifPresent(searchBuilder::head);
        runContext.render(this.createdByMe).as(Boolean.class).filter(b -> b).ifPresent(r -> searchBuilder.createdByMe());
        runContext.render(this.author).as(String.class).ifPresent(searchBuilder::author);

        PagedSearchIterable<GHPullRequest> pullRequests = searchBuilder.list();

        return SearchService.run(runContext, pullRequests, gitHub);
    }


    public static class GHPullRequestSearchBuilderCustom {
        private final GHPullRequestSearchBuilder searchBuilder;
        private final List<String> terms = new ArrayList<>();

        public GHPullRequestSearchBuilderCustom(GitHub gitHub) {
            this.searchBuilder = gitHub.searchPullRequests();
        }

        public GHPullRequestSearchBuilderCustom q(String qualifier, String value) {
            if (StringUtils.isEmpty(qualifier)) {
                throw new IllegalArgumentException("qualifier cannot be null or empty");
            }
            if (StringUtils.isEmpty(value)) {
                final String removeQualifier = qualifier + ":";
                terms.removeIf(term -> term.startsWith(removeQualifier));
            } else {
                terms.add(qualifier + ":" + value);
            }
            return this;
        }

        public GHPullRequestSearchBuilderCustom q(String value) {
            searchBuilder.q(value);
            return this;
        }

        public GHPullRequestSearchBuilderCustom repo(String repository) {
            q("repo", repository);
            return this;
        }

        public GHPullRequestSearchBuilderCustom author(String user) {
            q("author", user);
            return this;
        }

        public GHPullRequestSearchBuilderCustom createdByMe() {
            searchBuilder.createdByMe();
            return this;
        }

        public GHPullRequestSearchBuilderCustom assigned(String user) {
            q("assignee", user);
            return this;
        }

        public GHPullRequestSearchBuilderCustom mentions(String user) {
            q("mentions", user);
            return this;
        }

        public GHPullRequestSearchBuilderCustom isOpen() {
            searchBuilder.isOpen();
            return this;
        }

        public GHPullRequestSearchBuilderCustom isClosed() {
            searchBuilder.isClosed();
            return this;
        }

        public GHPullRequestSearchBuilderCustom isMerged() {
            searchBuilder.isMerged();
            return this;
        }

        public GHPullRequestSearchBuilderCustom isDraft() {
            searchBuilder.isDraft();
            return this;
        }

        public GHPullRequestSearchBuilderCustom head(String branch) {
            q("head", branch);
            return this;
        }

        public GHPullRequestSearchBuilderCustom base(String branch) {
            q("base", branch);
            return this;
        }

        public GHPullRequestSearchBuilderCustom commit(String sha) {
            q("SHA", sha);
            return this;
        }

        public GHPullRequestSearchBuilderCustom created(String created) {
            q("created", created);
            return this;
        }

        public GHPullRequestSearchBuilderCustom merged(String merged) {
            q("merged", merged);
            return this;
        }

        public GHPullRequestSearchBuilderCustom closed(String closed) {
            q("closed", closed);
            return this;
        }

        public GHPullRequestSearchBuilderCustom updated(String updated) {
            q("updated", updated);
            return this;
        }

        public GHPullRequestSearchBuilderCustom label(String label) {
            searchBuilder.label(label);
            return this;
        }

        public GHPullRequestSearchBuilderCustom inLabels(Iterable<String> labels) {
            searchBuilder.inLabels(labels);
            return this;
        }

        public GHPullRequestSearchBuilderCustom titleLike(String title) {
            searchBuilder.titleLike(title);
            return this;
        }

        public GHPullRequestSearchBuilderCustom order(GHDirection direction) {
            searchBuilder.order(direction);
            return this;
        }

        public GHPullRequestSearchBuilderCustom sort(GHPullRequestSearchBuilder.Sort sort) {
            searchBuilder.sort(sort);
            return this;
        }

        public PagedSearchIterable<GHPullRequest> list() {
            return searchBuilder.q(StringUtils.join(terms, " ")).list();
        }

    }

}
