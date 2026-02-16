package io.kestra.plugin.github.users;

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
    title = "Search GitHub users",
    description = "Runs the GitHub user search API and writes matches to storage. Defaults to join-date ascending. Anonymous access skips private data and some fields; provide OAuth/JWT to lift limits."
)
@Plugin(
    examples = {
        @Example(
            title = "Search for users.",
            full = true,
            code = """
                   id: github_user_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_users
                       type: io.kestra.plugin.github.users.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: "kestra-io in:login language:java"
                   """
        ),
        @Example(
            title = "Search for users with conditions.",
            full = true,
            code = """
                   id: github_user_search_flow
                   namespace: company.team

                   tasks:
                     - id: search_users
                       type: io.kestra.plugin.github.users.Search
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       query: kestra-io
                       in: login
                       language: java
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
        JOINED(GHUserSearchBuilder.Sort.JOINED),
        REPOSITORIES(GHUserSearchBuilder.Sort.REPOSITORIES),
        FOLLOWERS(GHUserSearchBuilder.Sort.FOLLOWERS);

        private final GHUserSearchBuilder.Sort value;
    }

    @RequiredArgsConstructor
    public enum Type {
        USER("user"),
        ORGANIZATION("org");

        private final String value;
    }

    @Schema(
        title = "Search keywords and qualifiers",
        description = "User search syntax combining keywords with qualifiers like location, followers, language."
    )
    private Property<String> query;

    @Schema(
        title = "Language filter",
        description = "Language name or alias applied to repositories the user owns."
    )
    private Property<String> language;

    @Schema(
        title = "Joined date filter",
        description = "Supports `>`, `<`, and range (`..`) syntax with YYYY-MM-DD."
    )
    private Property<String> created;

    @Schema(
        title = "Repository count filter",
        description = "Exact repository count to match."
    )
    private Property<Integer> repositories;

    @Schema(
        title = "Fields to search (`in:`)",
        description = "Restrict search to login, full name, or public email; supports `is:sponsorable`."
    )
    private Property<String> in;

    @Schema(
        title = "Location filter",
        description = "Matches the profile location field."
    )
    private Property<String> location;

    @Schema(
        title = "Followers filter",
        description = "Supports `>`, `<`, and range (`..`) follower counts."
    )
    private Property<String> followers;

    @Schema(
        title = "Account type",
        description = "USER returns individuals; ORGANIZATION returns org accounts."
    )
    private Property<Type> accountType;

    @Schema(
        title = "Sort direction",
        description = "ASC sorts ascending (default); DESC sorts descending."
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort field",
        description = "JOINED sorts by join date (default); REPOSITORIES by public repo count; FOLLOWERS by follower count."
    )
    @Builder.Default
    private Property<Sort> sort = Property.ofValue(Sort.JOINED);

    @Override
    public FileOutput run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHUserSearchBuilder searchBuilder = setupSearchParameters(runContext, gitHub);

        PagedSearchIterable<GHUser> users = searchBuilder.list();

        return this.run(runContext, users, gitHub);
    }

    private GHUserSearchBuilder setupSearchParameters(RunContext runContext, GitHub gitHub) throws Exception {
        GHUserSearchBuilder searchBuilder = gitHub.searchUsers();

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query).as(String.class).orElseThrow());
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language).as(String.class).orElseThrow());
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created).as(String.class).orElseThrow());
        }

        if (this.repositories != null) {
            searchBuilder.repos(runContext.render(repositories).as(Integer.class).orElseThrow().toString());
        }

        if (this.in != null) {
            searchBuilder.in(runContext.render(this.in).as(String.class).orElseThrow());
        }

        if (this.location != null) {
            searchBuilder.location(runContext.render(this.location).as(String.class).orElseThrow());
        }

        if (this.followers != null) {
            searchBuilder.followers(runContext.render(this.followers).as(String.class).orElseThrow());
        }

        if (this.accountType != null) {
            searchBuilder.type(runContext.render(this.accountType).as(Type.class).orElseThrow().value);
        }
        return searchBuilder;
    }

}
