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

import java.io.IOException;
import java.net.URL;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Add or update an issue comment",
    description = "Posts a comment on an existing GitHub issue or pull request. By default a new comment is created on each run. " +
        "Set `commentId` to update a specific comment in place, or set `updateTag` to keep a single, continuously-updated comment " +
        "(for example a test report that is refreshed on every run instead of piling up new comments). " +
        "The authenticated token must be allowed to read the repository and comment on the issue."
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
        ),
        @Example(
            title = "Keep a single, self-updating test report comment on a pull request. Using `updateTag`, the task looks for a previous comment carrying the same tag and updates it in place, otherwise it creates a new one.",
            full = true,
            code = """
                   id: github_report_test_results
                   namespace: company.team

                   tasks:
                     - id: report
                       type: io.kestra.plugin.github.issues.Comment
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       issueNumber: 1347
                       updateTag: test-report
                       body: |
                         ## Test report
                         {{ outputs.tests.report }}
                   """
        ),
        @Example(
            title = "Update a specific comment by its identifier.",
            full = true,
            code = """
                   id: github_update_comment
                   namespace: company.team

                   tasks:
                     - id: update_comment
                       type: io.kestra.plugin.github.issues.Comment
                       oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                       repository: kestra-io/kestra
                       issueNumber: 1347
                       commentId: 1234567890
                       body: "Updated at {{ now() }}"
                   """
        )
    }
)
public class Comment extends AbstractGithubTask implements RunnableTask<Comment.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format containing the issue"
    )
    @PluginProperty(group = "destination")
    private Property<String> repository;

    @Schema(
        title = "Issue number",
        description = "Numeric issue identifier within the repository. Pull requests share the same numbering as issues."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Integer> issueNumber;

    @Schema(
        title = "Comment body",
        description = "Markdown body to post as the issue comment. This value is rendered before the request is sent"
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    @Schema(
        title = "Comment ID",
        description = "Identifier of an existing comment to update in place. When set, the task updates that comment instead of " +
            "creating a new one and fails if no comment with this id exists on the issue. Takes precedence over `updateTag`."
    )
    @PluginProperty(group = "main")
    private Property<Long> commentId;

    @Schema(
        title = "Update tag",
        description = "A stable identifier used to keep a single, continuously-updated comment. It is embedded in the comment body " +
            "as a hidden HTML marker. When set, the task looks for an existing comment on the issue carrying the same tag and " +
            "updates it in place; if none is found, a new tagged comment is created. Ideal for recurring reports (e.g. a test " +
            "report) where you want one comment that gets refreshed instead of a new comment on every run. Ignored when `commentId` is set."
    )
    @PluginProperty(group = "main")
    private Property<String> updateTag;

    @Override
    public Comment.Output run(RunContext runContext) throws Exception {
        GitHub gitHub = connect(runContext);

        GHIssue issue = gitHub
            .getRepository(runContext.render(this.repository).as(String.class).orElse(null))
            .getIssue(runContext.render(this.issueNumber).as(Integer.class).orElseThrow());

        String renderedBody = runContext.render(this.body).as(String.class).orElse("");
        Long rCommentId = runContext.render(this.commentId).as(Long.class).orElse(null);
        String rTag = runContext.render(this.updateTag).as(String.class)
            .filter(tag -> !tag.isBlank())
            .orElse(null);

        GHIssueComment comment;
        boolean updated;

        if (rCommentId != null) {
            comment = findCommentById(issue, rCommentId);
            if (comment == null) {
                throw new IllegalArgumentException(
                    "No comment found with id " + rCommentId + " on issue #" + issue.getNumber()
                );
            }
            comment.update(renderedBody);
            updated = true;
        } else if (rTag != null) {
            String marker = marker(rTag);
            String taggedBody = renderedBody + "\n\n" + marker;

            comment = findCommentByMarker(issue, marker);
            if (comment != null) {
                comment.update(taggedBody);
                updated = true;
            } else {
                comment = issue.comment(taggedBody);
                updated = false;
            }
        } else {
            comment = issue.comment(renderedBody);
            updated = false;
        }

        return Output
            .builder()
            .issueUrl(issue.getHtmlUrl())
            .commentUrl(comment.getHtmlUrl())
            .commentId(comment.getId())
            .updated(updated)
            .build();
    }

    private static String marker(String tag) {
        return "<!-- kestra:comment-tag:" + tag + " -->";
    }

    private static GHIssueComment findCommentById(GHIssue issue, long id) throws IOException {
        for (GHIssueComment comment : issue.listComments()) {
            if (comment.getId() == id) {
                return comment;
            }
        }
        return null;
    }

    private static GHIssueComment findCommentByMarker(GHIssue issue, String marker) throws IOException {
        for (GHIssueComment comment : issue.listComments()) {
            String body = comment.getBody();
            if (body != null && body.contains(marker)) {
                return comment;
            }
        }
        return null;
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
            description = "GitHub URL for the created or updated comment"
        )
        private URL commentUrl;

        @Schema(
            title = "Comment ID",
            description = "Identifier of the created or updated comment. Feed this back into `commentId` to update the same comment later."
        )
        private Long commentId;

        @Schema(
            title = "Updated",
            description = "`true` when an existing comment was updated in place, `false` when a new comment was created."
        )
        private Boolean updated;
    }

}
