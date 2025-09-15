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
    title = "Search for GitHub users.",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information."
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
                       oauthToken: your_github_token
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
                       oauthToken: your_github_token
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
        title = "The query contains one or more search keywords and qualifiers.",
        description = "Qualifiers allow you to limit your search to specific areas of GitHub."
    )
    private Property<String> query;

    @Schema(
        title = "Search for users based on the languages of repositories they own.",
        description = "Can be the language name or alias."
    )
    private Property<String> language;

    @Schema(
        title = "Filter users based on when they joined GitHub.",
        description = """
                    Available formats:\n
                    - '<=YYYY-MM-DD' - joined at or before\n
                    - '>=YYYY-MM-DD' - joined at or after\n
                    - Similar cases for above two with ">", "<"\n
                    - 'YYYY-MM-DD..YYYY-MM-DD' - joined in period between
                    """
    )
    private Property<String> created;

    @Schema(
        title = "You can filter users based on the number of repositories they own."
    )
    private Property<Integer> repositories;

    @Schema(
        title = "With the 'in' qualifier you can restrict your search to the username/login, full name, public email.",
        description = "Example kenya in:login matches users with the word \"kenya\" in their username. " +
            "One more case of use to search users that have sponsor profile, equivalent to query: `is:sponsorable`."
    )
    private Property<String> in;

    @Schema(
        title = "Search for users by the location indicated in their profile."
    )
    private Property<String> location;

    @Schema(
        title = "Filter users based on the number of followers that they have."
    )
    private Property<String> followers;

    @Schema(
        title = "Restrict search results to personal accounts or organizations only.",
        description = """
                      USER - the results will include only user accounts\n
                      ORGANIZATION - the results will include only organization accounts
                      """
    )
    private Property<Type> accountType;

    @Schema(
        title = "Order of the output.",
        description = """
                      ASC - the results will be in ascending order (DEFAULT)\n
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    private Property<Order> order = Property.ofValue(Order.ASC);

    @Schema(
        title = "Sort condition of the output.",
        description = """
                      JOINED - the results will be sorted by when user joined to Github (DEFAULT)\n
                      REPOSITORIES - the results will be sorted by the number of repositories owned by user\n
                      FOLLOWERS - the results will be sorted by the number of followers that user has
                      """
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
