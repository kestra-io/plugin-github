package io.kestra.plugin.github.issues;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class CommentTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of();

        // First create an issue
        var createTask = Create.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .title(Property.ofValue("Test Kestra Github plugin"))
            .body(Property.ofValue("This is a test for creating a new issue in repository by oauth token"))
            .labels(Property.ofValue(List.of("kestra", "test")))
            .build();

        Create.Output createOutput = createTask.run(runContext);

        assertThat(createOutput.getIssueUrl()).isNotNull();
        assertThat(createOutput.getIssueNumber()).isNotNull();

        int issueNumber = createOutput.getIssueNumber();

        // Then add a comment to the created issue
        var commentTask = Comment.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .issueNumber(Property.ofValue(issueNumber))
            .body(Property.ofValue("This comment is a test for creating a new comment in repository issue by oauth token"))
            .build();

        Comment.Output commentOutput = commentTask.run(runContext);

        assertThat(commentOutput.getIssueUrl()).isNotNull();
        assertThat(commentOutput.getCommentUrl()).isNotNull();
        assertThat(commentOutput.getCommentId()).isEqualTo(100L);
        assertThat(commentOutput.getUpdated()).isFalse();
    }

    @Test
    void updateByCommentId() throws Exception {
        var runContext = runContextFactory.of();

        var commentTask = Comment.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .issueNumber(Property.ofValue(42))
            .commentId(Property.ofValue(100L))
            .body(Property.ofValue("Updated body for an existing comment"))
            .build();

        Comment.Output commentOutput = commentTask.run(runContext);

        assertThat(commentOutput.getCommentUrl()).isNotNull();
        assertThat(commentOutput.getCommentId()).isEqualTo(100L);
        assertThat(commentOutput.getUpdated()).isTrue();
    }

    @Test
    void updateByTagFindsExistingComment() throws Exception {
        var runContext = runContextFactory.of();

        var commentTask = Comment.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .issueNumber(Property.ofValue(42))
            .updateTag(Property.ofValue("test-report"))
            .body(Property.ofValue("## Test report\nAll green"))
            .build();

        Comment.Output commentOutput = commentTask.run(runContext);

        assertThat(commentOutput.getCommentUrl()).isNotNull();
        assertThat(commentOutput.getCommentId()).isEqualTo(100L);
        assertThat(commentOutput.getUpdated()).isTrue();
    }

    @Test
    void updateByUnknownTagCreatesNewComment() throws Exception {
        var runContext = runContextFactory.of();

        var commentTask = Comment.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .issueNumber(Property.ofValue(42))
            .updateTag(Property.ofValue("unmatched-tag"))
            .body(Property.ofValue("## Test report\nFirst run"))
            .build();

        Comment.Output commentOutput = commentTask.run(runContext);

        assertThat(commentOutput.getCommentUrl()).isNotNull();
        assertThat(commentOutput.getCommentId()).isEqualTo(100L);
        assertThat(commentOutput.getUpdated()).isFalse();
    }
}
