package io.kestra.plugin.github.repositories;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Search for GitHub repositories.",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for Github repositories using query.",
            full = true,
            code = """
                   id: github_repo_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_repositories
                       type: io.kestra.plugin.github.repositories.Search
                       oauthToken: your_github_token
                       query: "repo:kestra-io/plugin-github"
                   """
        ),
        @Example(
            title = "Search for Github repositories using repository.",
            full = true,
            code = """
                   id: github_repo_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_repositories
                       type: io.kestra.plugin.github.repositories.Search
                       oauthToken: your_github_token
                       repository: kestra-io/plugin-github
                   """
        ),
        @Example(
            title = "Search for Github repositories and order the results.",
            full = true,
            code = """
                   id: github_repo_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_repositories
                       type: io.kestra.plugin.github.repositories.Search
                       oauthToken: your_github_token
                       query: "user:kestra-io language:java is:public"
                       sort: STARS
                       order: DESC
                   """
        ),
        @Example(
            title = "Search for Github repositories with filters like language and visibility, and order the results.",
            full = true,
            code = """
                   id: github_repo_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_repositories
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
        title = "To search the code in a specific repository.",
        description = "Example string: \"myUserName/MyRepository\". query equivalent: \"repo:myUserName/MyRepository\"."
    )
    private Property<String> repository;

    @Schema(
        title = "The query contains one or more search keywords and qualifiers.",
        description = "Qualifiers allow you to limit your search to specific areas of GitHub."
    )
    private Property<String> query;

    @Schema(
        title = "Search for code based on what language it's written in.",
        description = "Can be the language name or alias."
    )
    private Property<String> language;

    @Schema(
        title = "Search for code based on when repository was created."
    )
    private Property<String> created;

    @Schema(
        title = "Search for code based on how many starts repository has."
    )
    private Property<String> stars;

    @Schema(
        title = "Search the code in all repositories owned by a certain user.",
        description = "To search by organization, use: \"query: org:myOrganization\"."
    )
    private Property<String> user;

    @Schema(
        title = "Search the code by topic"
    )
    private Property<String> topic;

    @Schema(
        title = "Order of the output.",
        description = """
                      ASC - the results will be in ascending order\n
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    private Property<Order> order = Property.of(Order.ASC);

    @Schema(
        title = "Sort condition of the output.",
        description = """
                      UPDATED - the results will be sorted by when the repository was last updated\n
                      STARS - the results will be sorted by the number of stars the repository has\n
                      FORKS - the results will be sorted by the number of forks the repository has
                      """
    )
    @Builder.Default
    private Property<Sort> sort = Property.of(Sort.UPDATED);

    @Schema(
        title = "Search repository that have specified repositories. By default, it's search for all repositories.",
        description = """
                      PUBLIC - shows only public repositories\n
                      PRIVATE - shows only private repositories that are available for user who is searching\n
                      INTERNAL - shows only internal repositories
                      """
    )
    private Property<Visibility> visibility;

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
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        if (this.visibility != null) {
            searchBuilder.visibility(runContext.render(this.visibility).as(Visibility.class).orElseThrow().value);
        }

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language).as(String.class).orElseThrow());
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created).as(String.class).orElseThrow());
        }

        if (this.repository != null) {
            searchBuilder.repo(runContext.render(this.repository).as(String.class).orElseThrow());
        }

        if (this.stars != null) {
            searchBuilder.stars(runContext.render(this.stars).as(String.class).orElseThrow());
        }

        if (this.user != null) {
            searchBuilder.user(runContext.render(this.user).as(String.class).orElseThrow());
        }

        if (this.topic != null) {
            searchBuilder.topic(runContext.render(this.topic).as(String.class).orElseThrow());
        }
        return searchBuilder;
    }

}
