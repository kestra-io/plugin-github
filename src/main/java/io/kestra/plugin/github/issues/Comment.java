package io.kestra.plugin.github.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Add an issue comment",
    description = "Posts a comment on an existing GitHub issue. The authenticated token must be allowed to read the repository and comment on the issue."
)
@Plugin(
    examples = {
        @Example(
            title = "Put a comment on an issue in a repository.",
            full = true,
            code = """
                   id: github_comment_on_issue_flow
                   namespace: company.team

                   tasks:
                     - id: comment_on_issue
                       type: io.kestra.plugin.github.issues.Comment
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       issueNumber: 1347
                       body: "{{ execution.id }} has failed on {{ taskrun.startDate }}. See the link below for more details"
                   """
        )
    }
)
public class Comment extends AbstractGithubTask implements RunnableTask<Comment.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format containing the issue"
    )
    private Property<String> repository;

    @Schema(
        title = "Issue number",
        description = "Numeric issue identifier within the repository."
    )
    @NotNull
    private Property<Integer> issueNumber;

    @Schema(
        title = "Comment body",
        description = "Markdown body to post as the issue comment. This value is rendered before the request is sent"
    )
    private Property<String> body;

    @Override
    public Comment.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssue issue = gitHub
            .getRepository(runContext.render(this.repository).as(String.class).orElse(null))
            .getIssue(runContext.render(this.issueNumber).as(Integer.class).orElseThrow());

        GHIssueComment comment = issue.comment(runContext.render(this.body).as(String.class).orElse(null));

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .commentUrl(comment.getHtmlUrl())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Issue URL",
            description = "GitHub URL for the issue that received the comment"
        )
        private URL issueUrl;

        @Schema(
            title = "Comment URL",
            description = "GitHub URL for the created comment"
        )
        private URL commentUrl;
    }

}
