package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GitHub;

import java.net.URL;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an issue",
    description = "Creates a new GitHub issue in the target repository. The authenticated token must be allowed to open issues, and property values are rendered before the issue is created."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an issue in a repository using JWT token.",
            full = true,
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       jwtToken: "{{ secret('GITHUB_JWT_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                   """
        ),
        @Example(
            title = "Create an issue in a repository using OAuth token.",
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       login: your_github_login
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                   """
        ),
        @Example(
            title = "Create an issue in a repository with assignees.",
            code = """
                   id: github_issue_create_flow
                   namespace: company.team

                   tasks:
                     - id: create_issue
                       type: io.kestra.plugin.github.issues.Create
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       title: Workflow failed
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                       labels:
                         - bug
                         - workflow
                       assignees:
                         - MyDeveloperUserName
                         - MyDesignerUserName
                   """
        )
    }
)
public class Create extends AbstractGithubTask implements RunnableTask<Create.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format where the issue will be created"
    )
    private Property<String> repository;

    @Schema(
        title = "Issue title",
        description = "Short summary shown in GitHub."
    )
    private Property<String> title;

    @Schema(
        title = "Issue body",
        description = "Markdown body for the issue. This value is rendered before the request is sent"
    )
    private Property<String> body;

    @Schema(
        title = "Issue labels",
        description = "Labels to apply to the issue when it is created"
    )
    private Property<List<String>> labels;

    @Schema(
        title = "Issue assignees",
        description = "GitHub logins to assign when the issue is created"
    )
    private Property<List<String>> assignees;

    @Override
    public Create.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssueBuilder issueBuilder = gitHub
            .getRepository(runContext.render(this.repository).as(String.class).orElse(null))
            .createIssue(runContext.render(this.title).as(String.class).orElse(null))
            .body(runContext.render(this.body).as(String.class).orElse(null));

        if (this.labels != null) {
            runContext.render(labels).asList(String.class)
                .forEach(issueBuilder::label);
        }

        if (this.assignees != null) {
            runContext.render(assignees).asList(String.class)
                .forEach(issueBuilder::assignee);
        }

        GHIssue issue = issueBuilder.create();

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .issueNumber(issue.getNumber())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Issue URL",
            description = "GitHub URL for the created issue"
        )
        private URL issueUrl;

        @Schema(
            title = "Issue number",
            description = "Numeric issue number assigned by GitHub"
        )
        private Integer issueNumber;
    }

}
