package io.kestra.plugin.github.pulls;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.GithubConnector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.net.URL;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a GitHub pull request.",
    description = "If no authentication is provided, anonymous authentication will be used."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a pull request in a repository.",
            full = true,
            code = """
                   id: github_pulls_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_pull_request
                       type: io.kestra.plugin.github.pulls.Create
                       oauthToken: your_github_token
                       repository: kestra-io/kestra
                       sourceBranch: develop
                       targetBranch: main
                       title: Merge develop to main
                       body: "Request to merge changes from develop into main"
                   """
        )
    }
)
public class Create extends GithubConnector implements RunnableTask<Create.Output> {

    private Property<String> repository;

    @Schema(
        title = "Source/Head branch.",
        description = "Required. The name of the branch where your changes are implemented. For cross-repository pull requests in the same network, namespace head with a user like this: `username:branch`."
    )
    private Property<String> sourceBranch;

    @Schema(
        title = "Target/Base branch.",
        description = "Required. The name of the branch you want your changes pulled into. This should be an existing branch on the current repository."
    )
    private Property<String> targetBranch;

    @Schema(
        title = "Ticket title.",
        description = "Required. The title of the pull request."
    )
    private Property<String> title;

    @Schema(
        title = "Ticket body.",
        description = "The contents of the pull request. This is the markdown description of a pull request."
    )
    private Property<String> body;

    @Schema(
        title = "Whether maintainers can modify the pull request.",
        description = "Boolean value indicating whether maintainers can modify the pull request. Default is false."
    )
    @Builder.Default
    private Property<Boolean> maintainerCanModify = Property.of(Boolean.FALSE);

    @Schema(
        title = "Whether to create a draft pull request.",
        description = "Boolean value indicates whether to create a draft pull request or not. Default is false."
    )
    @Builder.Default
    private Property<Boolean> draft = Property.of(Boolean.FALSE);

    @Override
    public Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHRepository repo = gitHub.getRepository(runContext.render(this.repository).as(String.class).orElse(null));

        if (!repo.hasPullAccess()) {
            return Output.builder().build();
        }

        String head = runContext.render(this.sourceBranch).as(String.class).orElse(null);
        String base = runContext.render(this.targetBranch).as(String.class).orElse(null);

        GHPullRequest pullRequest = repo.createPullRequest(
            runContext.render(this.title).as(String.class).orElse(null),
            head,
            base,
            runContext.render(this.body).as(String.class).orElse(null),
            runContext.render(this.maintainerCanModify).as(Boolean.class).orElseThrow(),
            runContext.render(this.draft).as(Boolean.class).orElseThrow()
        );

        return Output
            .builder()
            .issueUrl(pullRequest.getIssueUrl())
            .pullRequestUrl(pullRequest.getHtmlUrl())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URL issueUrl;
        private URL pullRequestUrl;
    }

}
