package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.GithubConnector;
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
    title = "Create a GitHub issue.",
    description = "If no authentication is provided, anonymous authentication will be used."
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
                       jwtToken: your_github_jwt_token
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
                       oauthToken: your_github_token
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
                       oauthToken: your_github_token
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
public class Create extends GithubConnector implements RunnableTask<Create.Output> {

    private Property<String> repository;

    @Schema(
        title = "Ticket title."
    )
    private Property<String> title;

    @Schema(
        title = "Ticket body."
    )
    private Property<String> body;

    @Schema(
        title = "Ticket label.",
        description = "List of labels for ticket."
    )
    private Property<List<String>> labels;

    @Schema(
        title = "Ticket assignee.",
        description = "List of unique names of assignees."
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
        private URL issueUrl;
        private Integer issueNumber;
    }

}
