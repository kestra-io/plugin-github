package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Create a GitHub issue",
    description = "If no authentication is provided, anonymous authentication will be used"
)
@Plugin(
    examples = {
        @Example(
            title = "Create an issue in a repository",
            code = """
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
            title = "Create an issue in a repository",
            code = """
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
            title = "Create an issue in a repository",
            code = """
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

    private String repository;

    @Schema(
        title = "Ticket title"
    )
    @PluginProperty(dynamic = true)
    private String title;

    @Schema(
        title = "Ticket body"
    )
    @PluginProperty(dynamic = true)
    private String body;

    @Schema(
        title = "Ticket label",
        description = "List of labels for ticket"
    )
    @PluginProperty(dynamic = true)
    private List<String> labels;

    @Schema(
        title = "Ticket assignee",
        description = "List of unique names of assignees"
    )
    @PluginProperty(dynamic = true)
    private List<String> assignees;

    @Override
    public Create.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssueBuilder issueBuilder = gitHub
            .getRepository(runContext.render(this.repository))
            .createIssue(runContext.render(this.title))
            .body(runContext.render(this.body));

        if (this.labels != null) {
            runContext.render(labels)
                .forEach(issueBuilder::label);
        }

        if (this.assignees != null) {
            runContext.render(assignees)
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
