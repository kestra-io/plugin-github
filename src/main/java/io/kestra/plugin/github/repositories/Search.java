package io.kestra.plugin.github.repositories;

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
    title = "Search for github repositories",
    description = "If no authentication is provided, anonymous connection will be used. Anonymous connection can't retrieve full information"
)
@Plugin(
    examples = {
        @Example(
            code = """
                   id: repos
                   type: io.kestra.plugin.github.repositories.Search
                   oauthToken: your_github_token
                   query: "repo:kestra-io/plugin-github"
                   """
        ),
        @Example(
            code = """
                   id: repos
                   type: io.kestra.plugin.github.repositories.Search
                   oauthToken: your_github_token
                   repository: kestra-io/plugin-github
                   """
        ),
        @Example(
            code = """
                   id: repos
                   type: io.kestra.plugin.github.repositories.Search
                   oauthToken: your_github_token
                   query: "user:kestra-io language:java is:public"
                   sort: STARS
                   order: DESC
                   """
        ),
        @Example(
            code = """
                   id: repos
                   type: io.kestra.plugin.github.repositories.Search
                   oauthToken: your_github_token
                   user: kestra-io
                   language: java
                   visibility: PUBLIC
                   sort: STARS
                   order: DESC
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
        UPDATED(GHRepositorySearchBuilder.Sort.UPDATED),
        STARS(GHRepositorySearchBuilder.Sort.STARS),
        FORKS(GHRepositorySearchBuilder.Sort.FORKS);

        private final GHRepositorySearchBuilder.Sort value;
    }

    @RequiredArgsConstructor
    public enum Visibility {
        PUBLIC(GHRepository.Visibility.PUBLIC),
        PRIVATE(GHRepository.Visibility.PRIVATE),
        INTERNAL(GHRepository.Visibility.INTERNAL);

        private final GHRepository.Visibility value;
    }

    @Schema(
        name = "To search the code in a specific repository",
        description = "Example string: \"myUserName/MyRepository\". query equivalent: \"repo:myUserName/MyRepository\""
    )
    @PluginProperty(dynamic = true)
    private String repository;

    @Schema(
        name = "The query contains one or more search keywords and qualifiers",
        description = "Qualifiers allow you to limit your search to specific areas of GitHub"
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        name = "Search for code based on what language it's written in",
        description = "Can be the language name or alias"
    )
    @PluginProperty(dynamic = true)
    private String language;

    @Schema(
        name = "Search for code based on when repository was created"
    )
    @PluginProperty(dynamic = true)
    private String created;

    @Schema(
        name = "Search for code based on how many starts repository has"
    )
    @PluginProperty(dynamic = true)
    private String stars;

    @Schema(
        name = "Search the code in all repositories owned by a certain user",
        description = "To search by organization use: \"query: org:myOrganization\""
    )
    @PluginProperty(dynamic = true)
    private String user;

    @Schema(
        name = "Search the code by topic"
    )
    @PluginProperty(dynamic = true)
    private String topic;

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
                      UPDATED - the results will be sorted by when the repository was last updated
                      STARS - the results will be sorted by the number of stars the repository has
                      FORKS - the results will be sorted by the number of forks the repository has
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.UPDATED;

    @Schema(
        name = "Search repository that have specified repositories. By default it's search for all repositories",
        description = """
                      PUBLIC - shows only public repositories
                      PRIVATE - shows only private repositories that are available for user who is searching
                      INTERNAL - shows only internal repositories
                      """
    )
    @PluginProperty
    private Visibility visibility;

    @Override
    public FileOutput run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHRepositorySearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHRepository> repositories = searchBuilder.list();

        return this.run(runContext, repositories, gitHub);
    }

    private GHRepositorySearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHRepositorySearchBuilder searchBuilder = gitHub.searchRepositories();

        searchBuilder
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.visibility != null) {
            searchBuilder.visibility(this.visibility.value);
        }

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language));
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created));
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository));
        }

        if (this.stars != null) {
            searchBuilder.stars(runContext.render(this.stars));
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user));
        }

        if (this.topic != null) {
            searchBuilder.topic(runContext.render(this.topic));
        }
        return searchBuilder;
    }

}
