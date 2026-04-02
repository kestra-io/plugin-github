package io.kestra.plugin.github.users;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import io.kestra.plugin.github.model.UserDetails;
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
    title = "Search users",
    description = "Runs a GitHub user search and writes matching user metadata to Kestra internal storage. Anonymous execution skips private data and may omit fields, and authenticated runs default to `JOINED` sorted in ascending order."
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
public class Search extends AbstractGithubSearchTask implements RunnableTask<AbstractGithubSearchTask.Output> {

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
        description = "Language name or alias applied to repositories the user owns"
    )
    private Property<String> language;

    @Schema(
        title = "Joined date filter",
        description = "Supports `>`, `<`, and range (`..`) syntax with YYYY-MM-DD"
    )
    private Property<String> created;

    @Schema(
        title = "Repository count filter",
        description = "Exact repository count to match"
    )
    private Property<Integer> repositories;

    @Schema(
        title = "Fields to search (`in:`)",
        description = "Restricts the search to fields accepted by GitHub such as `login`, `fullname`, or `email`"
    )
    private Property<String> in;

    @Schema(
        title = "Location filter",
        description = "Matches the public profile location field"
    )
    private Property<String> location;

    @Schema(
        title = "Followers filter",
        description = "Supports `>`, `<`, and range (`..`) follower counts"
    )
    private Property<String> followers;

    @Schema(
        title = "Account type",
        description = "USER returns individual accounts and ORGANIZATION returns organization accounts"
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
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHUserSearchBuilder searchBuilder = gitHub.searchUsers();

        searchBuilder
            .sort(runContext.render(this.sort).as(Sort.class).orElseThrow().value)
            .order(runContext.render(this.order).as(Order.class).orElseThrow().direction);

        runContext.render(this.query).as(String.class).ifPresent(searchBuilder::q);
        runContext.render(this.language).as(String.class).ifPresent(searchBuilder::language);
        runContext.render(this.created).as(String.class).ifPresent(searchBuilder::created);
        runContext.render(this.repositories).as(Integer.class).map(Object::toString).ifPresent(searchBuilder::repos);
        runContext.render(this.in).as(String.class).ifPresent(searchBuilder::in);
        runContext.render(this.location).as(String.class).ifPresent(searchBuilder::location);
        runContext.render(this.followers).as(String.class).ifPresent(searchBuilder::followers);
        runContext.render(this.accountType).as(Type.class).map(t -> t.value).ifPresent(searchBuilder::type);

        PagedSearchIterable<GHUser> users = searchBuilder.list();

        return handleFetch(
            runContext,
            users.toList(),
            throwFunction(user -> new UserDetails(user, gitHub.isAnonymous()).toMap()),
            runContext.render(fetchType).as(FetchType.class).orElseThrow()
        );
    }
}
