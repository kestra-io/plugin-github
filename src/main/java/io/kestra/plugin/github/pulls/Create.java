package io.kestra.plugin.github.pulls;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
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
    title = "Create a GitHub pull request",
    description = "Creates a pull request between branches in a repository. Requires authentication with push/PR permissions; defaults to nondraft and maintainers not allowed to edit."
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
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       sourceBranch: develop
                       targetBranch: main
                       title: Merge develop to main
                       body: "Request to merge changes from develop into main"
                   """
        )
    }
)
public class Create extends AbstractGithubTask implements RunnableTask<Create.Output> {
    @Schema(
        title = "Repository where the pull request will be created.",
        description = "Repository name must be in format owner/repo."
    )
    private Property<String> repository;

    @Schema(
        title = "Source (head) branch",
        description = "Required branch with changes. For cross-repo PRs, prefix with `username:branch`."
    )
    private Property<String> sourceBranch;

    @Schema(
        title = "Target (base) branch",
        description = "Required branch to merge into; must exist in the target repository."
    )
    private Property<String> targetBranch;

    @Schema(
        title = "Pull request title",
        description = "Required short summary."
    )
    private Property<String> title;

    @Schema(
        title = "Pull request body",
        description = "Markdown description; optional."
    )
    private Property<String> body;

    @Schema(
        title = "Allow maintainers to modify",
        description = "If true, maintainers of the target repo can push to the source branch. Defaults to false."
    )
    @Builder.Default
    private Property<Boolean> maintainerCanModify = Property.ofValue(Boolean.FALSE);

    @Schema(
        title = "Create as draft",
        description = "If true, opens the pull request in draft state. Defaults to false."
    )
    @Builder.Default
    private Property<Boolean> draft = Property.ofValue(Boolean.FALSE);

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
        @Schema(
            title = "The API Response URL for the Pull Request"
        )
        private URL issueUrl;
        @Schema(
            title = "The URL to the Pull Request"
        )
        private URL pullRequestUrl;
    }

}
