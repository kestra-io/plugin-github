package io.kestra.plugin.github.pulls;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class CreatePullRequestTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of();

        var task = Create.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .sourceBranch(Property.ofValue("dev"))
            .targetBranch(Property.ofValue("test"))
            .title(Property.ofValue("Test Kestra Github plugin"))
            .body(Property.ofValue("This is a test for creating a new pull request in repository by oauth token"))
            .maintainerCanModify(Property.ofValue(true))
            .build();

        Create.Output run = task.run(runContext);

        assertThat(run.getIssueUrl()).isNotNull();
        assertThat(run.getPullRequestUrl()).isNotNull();
    }
}
