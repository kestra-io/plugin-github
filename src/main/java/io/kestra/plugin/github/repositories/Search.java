package io.kestra.plugin.github.repositories;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import io.kestra.plugin.github.model.RepositoryDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.*;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search repositories",
    description = "Runs a GitHub repository search and writes matching repository metadata to Kestra internal storage. Anonymous execution skips private repositories and may omit fields, and authenticated runs default to `UPDATED` sorted in ascending order."
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       user: kestra-io
                       language: java
                       visibility: PUBLIC
                       sort: STARS
                       order: DESC
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
        title = "Repository filter",
        description = "`owner/repo` value for the `repo:` qualifier."
    )
    private Property<String> repository;

    @Schema(
        title = "Search keywords and qualifiers",
        description = "Repository search syntax combining keywords with qualifiers like language, topic, stars."
    )
    private Property<String> query;

    @Schema(
        title = "Language filter",
        description = "Language name or alias for the `language:` qualifier."
    )
    private Property<String> language;

    @Schema(
        title = "Created date filter",
        description = "Supports `>`, `<`, and range (`..`) syntax."
    )
    private Property<String> created;

    @Schema(
        title = "Stars filter",
        description = "Star count qualifier; supports `>`, `<`, and range (`..`)."
    )
    private Property<String> stars;

    @Schema(
        title = "User scope",
        description = "Limits search to repositories owned by the given user; use `org:` within `query` for organizations."
    )
    private Property<String> user;

    @Schema(
        title = "Topic filter",
        description = "Topic name used for the `topic:` qualifier"
    )
    private Property<String> topic;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "UPDATED sorts by last update time (default); STARS by star count; FORKS by fork count."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.UPDATED);

    @Schema(
        title = "Visibility filter",
        description = "PUBLIC limits results to public repositories, PRIVATE to private repositories visible to the token, and INTERNAL to internal repositories"
    )
    private Property<Visibility> visibility;

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHRepositorySearchBuilder searchBuilder = gitHub.searchRepositories();

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        runContext.render(this.visibility).as(Visibility.class).map(r -> r.value).ifPresent(searchBuilder::visibility);
        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::q);
        runContext.render(this.language).as(String.class).ifPresent(searchBuilder::language);
        runContext.render(this.created).as(String.class).ifPresent(searchBuilder::created);
        runContext.render(this.repository).as(String.class).ifPresent(searchBuilder::repo);
        runContext.render(this.stars).as(String.class).ifPresent(searchBuilder::stars);
        runContext.render(this.user).as(String.class).ifPresent(searchBuilder::user);
        runContext.render(this.topic).as(String.class).ifPresent(searchBuilder::topic);

        PagedSearchIterable<GHRepository> repositories = searchBuilder.list();

        return handleFetch(
            runContext,
            repositories.toList(),
            throwFunction(repository -> new RepositoryDetails(repository, gitHub.isAnonymous()).toMap()),
            runContext.render(fetchType).as(FetchType.class).orElseThrow()
        );
    }
}
