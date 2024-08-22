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
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GitHub;

import java.net.URL;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create GitHub issue comment.",
    description = "If no authentication is provided, anonymous authentication will be used."
)
@Plugin(
    examples = {
        @Example(
            code = """
                   id: comment
                   type: io.kestra.plugin.github.issues.Comment
                   oauthToken: your_github_token
                   repository: kestra-io/kestra
                   issueNumber: 1347
                   body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                   """
        )
    }
)
public class Comment extends GithubConnector implements RunnableTask<Comment.Output> {

    private String repository;

    @Schema(
        title = "Ticket number."
    )
    @PluginProperty
    private Integer issueNumber;

    @Schema(
        title = "Ticket body."
    )
    @PluginProperty(dynamic = true)
    private String body;

    @Override
    public Comment.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssue issue = gitHub
            .getRepository(runContext.render(this.repository))
            .getIssue(this.issueNumber);

        GHIssueComment comment = issue.comment(runContext.render(this.body));

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .commentUrl(comment.getHtmlUrl())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private URL issueUrl;
        private URL commentUrl;
    }

}
