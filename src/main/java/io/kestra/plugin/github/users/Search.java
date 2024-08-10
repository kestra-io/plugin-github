package io.kestra.plugin.github.users;

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
    title = "Search for GitHub users",
    description = "If no authentication is provided, anonymous authentication will be used. Anonymous authentication can't retrieve full information"
)
@Plugin(
    examples = {
        @Example(
            code = """
                   oauthToken: your_github_token
                   query: "kestra-io in:login language:java"
                   """
        ),
        @Example(
            code = """
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
        title = "The query contains one or more search keywords and qualifiers",
        description = "Qualifiers allow you to limit your search to specific areas of GitHub"
    )
    @PluginProperty(dynamic = true)
    private String query;

    @Schema(
        title = "Search for users based on the languages of repositories they own",
        description = "Can be the language name or alias"
    )
    @PluginProperty(dynamic = true)
    private String language;

    @Schema(
        title = "Filter users based on when they joined GitHub",
        description = """
                    Available formats:
                    - '<=YYYY-MM-DD' - joined at or before
                    - '>=YYYY-MM-DD' - joined at or after
                    - Similar cases for above two with ">", "<"
                    - 'YYYY-MM-DD..YYYY-MM-DD' - joined in period between
                    """
    )
    @PluginProperty(dynamic = true)
    private String created;

    @Schema(
        title = "You can filter users based on the number of repositories they own"
    )
    @PluginProperty(dynamic = true)
    private Integer repositories;

    @Schema(
        title = "With the 'in' qualifier you can restrict your search to the username/login, full name, public email",
        description = "Example kenya in:login matches users with the word \"kenya\" in their username. " +
            "One more case of use to search users that have sponsor profile, equivalent to query: is:sponsorable"
    )
    @PluginProperty(dynamic = true)
    private String in;

    @Schema(
        title = "Search for users by the location indicated in their profile"
    )
    @PluginProperty(dynamic = true)
    private String location;

    @Schema(
        title = "Filter users based on the number of followers that they have"
    )
    @PluginProperty(dynamic = true)
    private String followers;

    @Schema(
        title = "Restrict search results to personal accounts or organizations only",
        description = """
                      USER - the results will include only user accounts
                      ORGANIZATION - the results will include only organization accounts
                      """
    )
    @PluginProperty(dynamic = true)
    private Type accountType;

    @Schema(
        title = "Order output",
        description = """
                      ASC - the results will be in ascending order (DEFAULT)
                      DESC - the results will be in descending order
                      """
    )
    @Builder.Default
    @PluginProperty
    private Order order = Order.ASC;

    @Schema(
        title = "Sort output",
        description = """
                      JOINED - the results will be sorted by when user joined to Github (DEFAULT)
                      REPOSITORIES - the results will be sorted by the number of repositories owned by user
                      FOLLOWERS - the results will be sorted by the number of followers that user has
                      """
    )
    @Builder.Default
    @PluginProperty
    private Sort sort = Sort.JOINED;

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
            .sort(this.sort.value)
            .order(this.order.direction);

        if (this.query != null) {
            searchBuilder.q(runContext.render(this.query));
        }

        if (this.language != null) {
            searchBuilder.language(runContext.render(this.language));
        }

        if (this.created != null) {
            searchBuilder.created(runContext.render(this.created));
        }

        if (this.repositories != null) {
            searchBuilder.repos(this.repositories.toString());
        }

        if (this.in != null) {
            searchBuilder.in(runContext.render(this.in));
        }

        if (this.location != null) {
            searchBuilder.location(runContext.render(this.location));
        }

        if (this.followers != null) {
            searchBuilder.followers(runContext.render(this.followers));
        }

        if (this.accountType != null) {
            searchBuilder.type(this.accountType.value);
        }
        return searchBuilder;
    }

}
